package com.example.camera2;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {
    private TextureView camreaView;
    private String cameraId;
    private Size preViewSize;
    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private  CameraDevice cameraDevice;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int state = STATE_PREVIEW;
    private Button btnTakePhoto;
    private ImageReader imageReader;
    private File file;
    public Bitmap rotateBitmap(Bitmap bm, String path) {

        Bitmap rotatedBitmap = null;

        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:

                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(270);
                    break;

            }

            rotatedBitmap = Bitmap.createBitmap(bm,0,0,bm.getWidth(),bm.getHeight(),matrix,true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rotatedBitmap;
    }
    ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.e("IMAGE","onImageAvailableListener");
            File pictureFileDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Photographer");
            if (!pictureFileDir.exists()) {
                boolean isDirectoryCreated = pictureFileDir.mkdirs();
                if(!isDirectoryCreated)
                    Log.i("ATG", "Can't create directory to save the image");
            }
            String filename = pictureFileDir.getPath() +File.separator+ System.currentTimeMillis()+".jpg";
            file = new File(filename);
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                save(bytes);
                Intent intent = new Intent(MainActivity.this,PreviewImageActivity.class);
                intent.putExtra("image",file.getAbsolutePath());
                startActivity(intent);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }


        // Lưu ảnh



//Convert bitmap to byte array



        }
    };
    private void save(byte[] bytes) throws IOException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } finally {
            if (null != output) {
                output.close();
            }
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},1 );
        startBackgroundThread();


    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    private void stopBackgroundThread() {
        if(backgroundThread != null)
        backgroundThread.quitSafely();
        try {
            assert backgroundThread != null;
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void initView() {
        camreaView =findViewById(R.id.camreaView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
                //lockFocus();
            }
        });
    }
    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            setupCamera(i,i1);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };



    private void openCamera() {

        try {
            // mở kết nối tới Camera của thiết bị
            // các hành động trả về sẽ dc thực hiện trong "cameraDeviceStateCallback"
            // tham số thứ 3 của hàm openCamera là 1 "Handler"
            // nhưng hiện tại ở đây chúng ta chưa cần thiết nên mình để nó là "null"
            CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
            if(backgroundHandler ==null){
                Log.e("EEE","nullll");
            }
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback,backgroundHandler);
        } catch (Exception  e) {
            e.printStackTrace();
            Log.e("IAMGE",e.getMessage());

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupCamera(int width, int height){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = null;

                try {
                    cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }


                // ở đây mình sử dụng Camera sau để thực hiện bài test.
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map =
                        cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // Set Size để hiển thị lên màn hình
                preViewSize = getPreferredPreviewsSize(
                        map.getOutputSizes(SurfaceTexture.class),
                        width,
                        height);
                cameraId = id;
            }
            imageReader = ImageReader.newInstance(480,960, ImageFormat.JPEG,1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private Size getPreferredPreviewsSize(Size[] mapSize, int width, int height) {
        List<Size> collectorSize = new ArrayList<>();
        for (Size option : mapSize) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSize.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    collectorSize.add(option);
                }
            }
        }
        if (collectorSize.size() > 0) {
            return Collections.min(collectorSize, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                }
            });
        }
        return mapSize[0];
    }

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;

            // Hiển thị hình ảnh thu về từ Camera lên màn hình
            createCameraPreviewSession();
            Toast.makeText(getApplicationContext(),"Camera open", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice =null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice =null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = camreaView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(preViewSize.getWidth(), preViewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            // Khởi tạo CaptureRequestBuilder từ cameraDevice với template truyền vào là
            // "CameraDevice.TEMPLATE_PREVIEW"
            // Với template này thì CaptureRequestBuilder chỉ thực hiện View mà thôi
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Thêm đích đến cho dữ liệu lấy về từ Camera
            // Đích đến này phải nằm trong danh sách các đích đến của dữ liệu
            // được định nghĩa trong cameraDevice.createCaptureSession() "phần định nghĩa này ngay bên dưới"
            previewCaptureRequestBuilder.addTarget(previewSurface);

            // Khởi tạo 1 CaptureSession
            // Arrays.asList(previewSurface) List danh sách các Surface
            // ( đích đến của hình ảnh thu về từ Camera)
            // Ở đây đơn giản là chỉ hiển thị hình ảnh thu về từ Camera nên chúng ta chỉ có 1 đối số.
            // Nếu bạn muốn chụp ảnh hay quay video vvv thì
            // ta có thể truyền thêm các danh sách đối số vào đây
            // Vd: Arrays.asList(previewSurface, imageReader)
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface,imageReader.getSurface()),
                    // Hàm Callback trả về kết quả khi khởi tạo.
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            try {
                                // Khởi tạo CaptureRequest từ CaptureRequestBuilder
                                // với các thông số đã được thêm ở trên
                                previewCaptureRequest = previewCaptureRequestBuilder.build();
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(
                                        previewCaptureRequest,
                                        cameraSessionCaptureCallback,
                                        null);
                                Log.e("IMAGE","onConfigured");

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(),
                                    "Create camera session fail", Toast.LENGTH_SHORT).show();
                        }
                    },
                    // Đối số thứ 3 của hàm là 1 Handler,
                    // nhưng do hiện tại chúng ta chưa làm gì nhiều nên mình tạm thời để là null
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private CameraCaptureSession.CaptureCallback cameraSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult result) {
                    switch (state) {
                        case STATE_PREVIEW:
                            // khi đang hiển thị preview thì ko làm gì cả
                            break;
                        case STATE_WAIT_LOCK:
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            // nếu trạng thái camera đã khóa được nét thì hiển thị Toast thông báo cho ng dùng
                            if (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                                unlockFocus();

                                Toast.makeText(getApplicationContext(), "Focus Lock", Toast.LENGTH_SHORT).show();
                            }
                            break;
                    }
                }
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                             long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                   // Log.e("IMAGE","onCaptureCompleted");

                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session,
                                            CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                }
            };

    private void takePhoto() {
        try {
            CaptureRequest.Builder captureStill =  cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            CameraCaptureSession.CaptureCallback captureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session,
                                                       CaptureRequest request,
                                                       TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Toast.makeText(getApplicationContext(),
                                    "Image Capture", Toast.LENGTH_SHORT).show();
                            unlockFocus();
                        }
                    };
            captureStill.addTarget(imageReader.getSurface());
            cameraCaptureSession.capture(captureStill.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void lockFocus() {
        try {
            state = STATE_WAIT_LOCK;
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);

            cameraCaptureSession.capture(previewCaptureRequestBuilder.build(),
                    cameraSessionCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void unlockFocus() {
        try {
            state = STATE_PREVIEW;
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);

            cameraCaptureSession.capture(previewCaptureRequestBuilder.build(),
                    cameraSessionCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();

        stopBackgroundThread();
        closeCamera();

        startBackgroundThread();

        if (camreaView.isAvailable()) {

            setupCamera(camreaView.getWidth(), camreaView.getHeight());
            openCamera();


        } else {
            Log.e("pause","resume");

            camreaView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

//    @Override
//    protected void onPause() {
//
//      //  stopBackgroundThread();
//
//        closeCamera();
//        Log.e("pause","pasue");
//        super.onPause();
//    }

//    @Override
//    protected void onDestroy() {
//        stopBackgroundThread();
//        closeCamera();
//        super.onDestroy();
//    }

    private void closeCamera() {
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice =null;
        }
    }
}
