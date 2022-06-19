package com.vhd.demoactivity;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ImageReaderFragment extends Fragment {

    private String TAG = getClass().getSimpleName();

    private TextureView previewContent;
    private ImageReader mPreviewImageReader;
    public HandlerThread mBackgroundThread;
    public Handler mBackgroundHandler;

    private int mWidth = 1920;
    private int mHeight = 1080;
    private int mCameraId = 0;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;

    static class SingleTon {
        private static final ImageReaderFragment INSTANCE = new ImageReaderFragment();
    }

    public static ImageReaderFragment instance() {
        return SingleTon.INSTANCE;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_img_reader, container, false);
        previewContent = view.findViewById(R.id.image_reader);
        startBackgroundThread();
        startPreview();
        return view;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void startPreview() {
        previewContent.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable");
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged");
            mWidth = width;
            mHeight = height;
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.e(TAG, "onSurfaceTextureDestroyed");
            closeCameraDevice();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            Log.e(TAG, "onSurfaceTextureUpdated");
        }
    };

    private void openCamera() {
        Log.e(TAG, "open camera");
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(String.valueOf(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {


        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.e(TAG, "onOpened");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    mCameraDevice = cameraDevice;
                    createCameraPreviewSession();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.e(TAG, "onDisconnected");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "onError");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void createCameraPreviewSession() {
        if (mCameraDevice == null) {
            return;
        }
        SurfaceTexture texture = previewContent.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mWidth, mHeight);

        // This is the output Surface we need to start preview.
        Surface surface = new Surface(texture);

        mPreviewImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, 2);
        mPreviewImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mPreviewImageReader.getSurface());
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(surface);
            surfaces.add(mPreviewImageReader.getSurface());
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigured");
                    try {
                        mCaptureSession = session;
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigured fail");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.e(TAG, "onImageAvailable format: " + reader.getImageFormat());
            Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            try {
                if (dumpFirst) {
                    dumpFirst = false;
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    int pixelStride = image.getPlanes()[0].getPixelStride();
                    int rowStride = image.getPlanes()[0].getRowStride();
                    Log.e(TAG, "y pixelStride: " + pixelStride + "   rowStride: " + rowStride);
                    byte[] bytes = new byte[mWidth * mHeight * 3 / 2];

                    int yLen = mWidth * mHeight;
                    byteBuffer.get(bytes, 0, yLen);

                    ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                    pixelStride = image.getPlanes()[1].getPixelStride();
                    rowStride = image.getPlanes()[1].getRowStride();
                    Log.e(TAG, "u pixelStride: " + pixelStride + "   rowStride: " + rowStride);
                    for (int i = 0; i < uBuffer.remaining(); i += pixelStride) {
                        bytes[yLen++] = uBuffer.get(i);
                    }

                    ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
                    pixelStride = image.getPlanes()[2].getPixelStride();
                    rowStride = image.getPlanes()[2].getRowStride();
                    Log.e(TAG, "v pixelStride: " + pixelStride + "   rowStride: " + rowStride);
                    for (int i = 0; i < vBuffer.remaining(); i += pixelStride) {
                        bytes[yLen++] = vBuffer.get(i);
                    }

                    String path = getActivity().getDataDir() + "/hello";
                    File file = new File(path);
                    if (!file.exists()) {
                        file.createNewFile();
                    }

                    Log.e(TAG, "save file path: " + file.getAbsolutePath());
                    FileOutputStream outputStream = new FileOutputStream(file);
                    outputStream.write(bytes);
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            image.close();
        }
    };

    private volatile boolean dumpFirst = true;

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            Log.e(TAG, "onCaptureProgressed");
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Log.e(TAG, "onCaptureCompleted");
        }

    };

    public void closeCameraDevice() {
        if (mCameraDevice != null) {
            synchronized (this) {
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        }
    }

}
