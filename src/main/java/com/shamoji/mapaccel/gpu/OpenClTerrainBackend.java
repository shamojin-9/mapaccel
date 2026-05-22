package com.shamoji.mapaccel.gpu;

import com.shamoji.mapaccel.MapAccel;
import com.shamoji.mapaccel.config.MapAccelConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opencl.CL10.CL_DEVICE_NAME;
import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_PLATFORM_NAME;
import static org.lwjgl.opencl.CL10.CL_PROGRAM_BUILD_LOG;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;

final class OpenClTerrainBackend implements GpuTerrainBackend {
    private static final int CELLS_PER_CHUNK = 16 * 16;
    private static final String KERNEL = """
            __kernel void mapaccel_preview(
                __global const int* chunk_xs,
                __global const int* chunk_zs,
                const int seed,
                __global int* checksums
            ) {
                int chunk = get_global_id(0);
                int chunk_x = chunk_xs[chunk];
                int chunk_z = chunk_zs[chunk];
                int checksum = 1;
                for (int gid = 0; gid < 256; gid++) {
                    int lx = gid & 15;
                    int lz = gid >> 4;
                    int wx = chunk_x * 16 + lx;
                    int wz = chunk_z * 16 + lz;
                    int h = wx * 374761393 + wz * 668265263 + seed * 1442695041;
                    h = (h ^ (h >> 13)) * 1274126177;
                    h = h ^ (h >> 16);
                    checksum = checksum * 31 + h;
                }
                checksums[chunk] = checksum;
            }
            """;

    private final long context;
    private final long queue;
    private final long program;
    private final long kernel;
    private final String deviceName;
    private final AtomicInteger computeRequests = new AtomicInteger();
    private final AtomicInteger computedChunks = new AtomicInteger();
    private final AtomicLong computeNanos = new AtomicLong();
    private volatile int lastChecksum;

    private OpenClTerrainBackend(long context, long queue, long program, long kernel, String deviceName) {
        this.context = context;
        this.queue = queue;
        this.program = program;
        this.kernel = kernel;
        this.deviceName = deviceName;
    }

    static GpuTerrainBackend createOrDisabled() {
        if (!MapAccelConfig.ENABLE_OPENCL_BACKEND.get()) {
            return DisabledGpuTerrainBackend.withReason("OpenCL disabled by config");
        }
        if (MapAccelConfig.DISABLE_OPENCL_WITH_EMBEDDIUM.get()
                && FMLEnvironment.dist == Dist.CLIENT
                && (ModList.get().isLoaded("embeddium") || ModList.get().isLoaded("rubidium"))) {
            return DisabledGpuTerrainBackend.withReason("OpenCL disabled: Embeddium compatibility mode");
        }
        try {
            ensureOpenClCreated();
            IntBuffer count = BufferUtils.createIntBuffer(1);
            int platformResult = CL10.clGetPlatformIDs(null, count);
            if (platformResult != CL_SUCCESS || count.get(0) <= 0) {
                return DisabledGpuTerrainBackend.withReason("no OpenCL platform");
            }
            PointerBuffer platforms = BufferUtils.createPointerBuffer(count.get(0));
            CL10.clGetPlatformIDs(platforms, (IntBuffer) null);

            for (int p = 0; p < platforms.capacity(); p++) {
                long platform = platforms.get(p);
                count.clear();
                int deviceResult = CL10.clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, null, count);
                if (deviceResult != CL_SUCCESS || count.get(0) <= 0) {
                    continue;
                }
                PointerBuffer devices = BufferUtils.createPointerBuffer(count.get(0));
                CL10.clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices, (IntBuffer) null);
                long device = devices.get(0);
                return createForDevice(platform, device);
            }
            return DisabledGpuTerrainBackend.withReason("no OpenCL GPU device");
        } catch (Throwable throwable) {
            return DisabledGpuTerrainBackend.withReason(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private static void ensureOpenClCreated() {
        try {
            CL.create();
        } catch (IllegalStateException ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("already been created")) {
                throw ex;
            }
        }
    }

    private static GpuTerrainBackend createForDevice(long platform, long device) {
        IntBuffer err = BufferUtils.createIntBuffer(1);
        long context = CL10.clCreateContext((PointerBuffer) null, device, null, 0L, err);
        if (err.get(0) != CL_SUCCESS) {
            return DisabledGpuTerrainBackend.withReason("clCreateContext failed: " + err.get(0));
        }
        err.clear();
        long queue = CL10.clCreateCommandQueue(context, device, 0L, err);
        if (err.get(0) != CL_SUCCESS) {
            CL10.clReleaseContext(context);
            return DisabledGpuTerrainBackend.withReason("clCreateCommandQueue failed: " + err.get(0));
        }
        err.clear();
        long program = CL10.clCreateProgramWithSource(context, KERNEL, err);
        if (err.get(0) != CL_SUCCESS) {
            CL10.clReleaseCommandQueue(queue);
            CL10.clReleaseContext(context);
            return DisabledGpuTerrainBackend.withReason("clCreateProgramWithSource failed: " + err.get(0));
        }
        int build = CL10.clBuildProgram(program, device, "", null, 0L);
        if (build != CL_SUCCESS) {
            String log = getProgramBuildInfoString(program, device, CL_PROGRAM_BUILD_LOG);
            CL10.clReleaseProgram(program);
            CL10.clReleaseCommandQueue(queue);
            CL10.clReleaseContext(context);
            return DisabledGpuTerrainBackend.withReason("clBuildProgram failed: " + log);
        }
        err.clear();
        long kernel = CL10.clCreateKernel(program, "mapaccel_preview", err);
        if (err.get(0) != CL_SUCCESS) {
            CL10.clReleaseProgram(program);
            CL10.clReleaseCommandQueue(queue);
            CL10.clReleaseContext(context);
            return DisabledGpuTerrainBackend.withReason("clCreateKernel failed: " + err.get(0));
        }
        String platformName = getPlatformInfoString(platform, CL_PLATFORM_NAME);
        String deviceName = platformName + " / " + getDeviceInfoString(device, CL_DEVICE_NAME);
        MapAccel.LOGGER.info("MapAccel OpenCL backend enabled: {}", deviceName);
        return new OpenClTerrainBackend(context, queue, program, kernel, deviceName);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public synchronized void warmup(ServerLevel level, ChunkPos pos) {
        warmupBatch(level, List.of(pos));
    }

    @Override
    public synchronized void warmupBatch(ServerLevel level, List<ChunkPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        computeRequests.incrementAndGet();
        long started = System.nanoTime();
        IntBuffer err = BufferUtils.createIntBuffer(1);
        IntBuffer chunkXs = BufferUtils.createIntBuffer(positions.size());
        IntBuffer chunkZs = BufferUtils.createIntBuffer(positions.size());
        for (ChunkPos pos : positions) {
            chunkXs.put(pos.x);
            chunkZs.put(pos.z);
        }
        chunkXs.flip();
        chunkZs.flip();
        long xBuffer = CL10.clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, chunkXs, err);
        if (err.get(0) != CL_SUCCESS) {
            return;
        }
        err.clear();
        long zBuffer = CL10.clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, chunkZs, err);
        if (err.get(0) != CL_SUCCESS) {
            CL10.clReleaseMemObject(xBuffer);
            return;
        }
        err.clear();
        long output = CL10.clCreateBuffer(context, CL_MEM_READ_WRITE, (long) positions.size() * Integer.BYTES, err);
        if (err.get(0) != CL_SUCCESS) {
            CL10.clReleaseMemObject(xBuffer);
            CL10.clReleaseMemObject(zBuffer);
            return;
        }
        try {
            int seed = Long.hashCode(level.getSeed());
            CL10.clSetKernelArg1p(kernel, 0, xBuffer);
            CL10.clSetKernelArg1p(kernel, 1, zBuffer);
            CL10.clSetKernelArg1i(kernel, 2, seed);
            CL10.clSetKernelArg1p(kernel, 3, output);
            PointerBuffer global = BufferUtils.createPointerBuffer(1);
            global.put(0, positions.size());
            int run = CL10.clEnqueueNDRangeKernel(queue, kernel, 1, null, global, null, null, null);
            if (run != CL_SUCCESS) {
                return;
            }
            IntBuffer checksums = BufferUtils.createIntBuffer(positions.size());
            int read = CL10.clEnqueueReadBuffer(queue, output, true, 0L, checksums, null, null);
            if (read != CL_SUCCESS) {
                return;
            }
            lastChecksum = checksum(checksums);
            computedChunks.addAndGet(positions.size());
            computeNanos.addAndGet(System.nanoTime() - started);
        } finally {
            CL10.clReleaseMemObject(output);
            CL10.clReleaseMemObject(zBuffer);
            CL10.clReleaseMemObject(xBuffer);
        }
    }

    @Override
    public String name() {
        return "opencl";
    }

    @Override
    public GpuStats snapshotAndReset() {
        return new GpuStats(
                computeRequests.getAndSet(0),
                computedChunks.getAndSet(0),
                computeNanos.getAndSet(0L),
                deviceName + " checksum=" + lastChecksum
        );
    }

    private int checksum(IntBuffer heights) {
        int hash = 1;
        for (int i = 0; i < heights.capacity(); i++) {
            hash = 31 * hash + heights.get(i);
        }
        return hash;
    }

    private static String getPlatformInfoString(long platform, int param) {
        PointerBuffer size = BufferUtils.createPointerBuffer(1);
        CL10.clGetPlatformInfo(platform, param, (ByteBuffer) null, size);
        ByteBuffer value = BufferUtils.createByteBuffer((int) size.get(0));
        CL10.clGetPlatformInfo(platform, param, value, null);
        return readNullTerminated(value);
    }

    private static String getDeviceInfoString(long device, int param) {
        PointerBuffer size = BufferUtils.createPointerBuffer(1);
        CL10.clGetDeviceInfo(device, param, (ByteBuffer) null, size);
        ByteBuffer value = BufferUtils.createByteBuffer((int) size.get(0));
        CL10.clGetDeviceInfo(device, param, value, null);
        return readNullTerminated(value);
    }

    private static String getProgramBuildInfoString(long program, long device, int param) {
        PointerBuffer size = BufferUtils.createPointerBuffer(1);
        CL10.clGetProgramBuildInfo(program, device, param, (ByteBuffer) null, size);
        ByteBuffer value = BufferUtils.createByteBuffer((int) size.get(0));
        CL10.clGetProgramBuildInfo(program, device, param, value, null);
        return readNullTerminated(value);
    }

    private static String readNullTerminated(ByteBuffer value) {
        int length = 0;
        while (length < value.capacity() && value.get(length) != 0) {
            length++;
        }
        byte[] bytes = new byte[length];
        value.get(0, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
