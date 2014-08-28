package com.paranoidgems.parallel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.sax.StartElementListener;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.ImageView;
import android.widget.Toast;

public class ParallelCameraPreview2
        implements
        SurfaceHolder.Callback,
        Camera.PreviewCallback {

    private static final int STATE_OFF = 0;
    private static final int STATE_NO_CALLBACKS = 1;
    private static final int STATE_PREVIEW = 2;
    private static final int STATE_PROCESS = 3;
    private static final int STATE_PROCESS_IN_PROGRESS = 4;
    private static final int STATE_PREVIEW_PICTURE = 5;
    private static final int STATE_PREVIEW_PICTURE_IN_PROGRESS = 6;

    private int mState = STATE_PREVIEW;

    private static final int EXPOSURE_PREVIEW = 1;
    private static final int CANVAS_PREVIEW = 2;

    private Camera mCamera = null;
    public Camera.Parameters params;
    private SurfaceHolder sHolder;
    private ProcessPreviewDataTask processDataTask;
    private GetPictureTask getPictureTask;

    private Bitmap bitmap = null;

    private String previewBitmapFileName; // preview bitmap to be saved

    private int getPictureTaskFor = 0;

    private int[] pixels = null;
    private float[] floatpixels = null;
    private byte[] frameData = null;

    private int imageFormat;

    private boolean bProcessing = false;
    private final int frameCount = 0;
    private int numFrames = 0;

    private int camId = 1;
    public int currentZoomLevel = 1;
    public float maxZoom;
    public List<Camera.Size> supportedSizes;

    public int isCamOpen = 0;
    public boolean isSizeSupported = false;
    private int height = 240;
    private int width = 320;
    private int previewWidth, previewHeight;
    private double alphaVal = 0.0002;

    private int minExp = 0, maxExp = 0;
    private boolean expCompenstationSupported = false;
    private int expCompValue = 0;
    private double expAngle = 0;

    private final boolean showPreview = false;
    private boolean doProcessing = false;
    private boolean threadInProcess = false;
    private boolean mProcessingFirstFrame = false;
    private boolean mProcessInProgress = false;
    private boolean isFocusable = false;
    private final ParallelActivity pact;

    private final static String TAG = "ParallelCameraPreview";

    Handler mHandler = new Handler(Looper.getMainLooper());

    public ParallelCameraPreview2(int width, int height, ParallelActivity parallelactivity,
                              int saved_cam_pref, double angle) {
        Log.i("campreview", "Width = " + String.valueOf(width));
        Log.i("campreview", "Height = " + String.valueOf(height));
        previewWidth = width;
        previewHeight = height;
        pact = lexactivity;
        camId = saved_cam_pref;
        expAngle = angle;
        Log.i(TAG, "expAngle - " + String.valueOf(expAngle));
    }

    private int openCamera(int cameraId) {
        if (isCamOpen == 1) {
            releaseCamera();
        }

        // this.currentZoomLevel = 1;

        if (cameraId == 0) {
            int fflag = 0;
            try {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                fflag = 1;
            } catch (Exception e) {
                fflag = 0;
            }

            if (fflag == 0) {
                try {
                    mCamera = Camera.open(1);
                    fflag = 1;
                } catch (Exception e) {
                    fflag = 0;
                }
            }

            if (fflag == 0) {
                camId = 1;
                this.changeCam(1);
                Toast.makeText(pact, "Unable to initialize front camera",
                        Toast.LENGTH_SHORT).show();
                return camId;
            }

        } else {
            int bflag = 0;
            try {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                bflag = 1;
            } catch (Exception e) {
                bflag = 0;
            }
            if (bflag == 0) {
                try {
                    mCamera = Camera.open(0); // for htc evo on which I tested it
                    bflag = 1;
                } catch (Exception e) {
                    bflag = 0;
                }
            }

            if (bflag == 0) {
                return -1;
            }
        }

        if (mCamera == null) {
            return -1;
        }

        params = mCamera.getParameters();

        minExp = params.getMinExposureCompensation();
        maxExp = params.getMaxExposureCompensation();

        if (minExp != 0 && maxExp !=0) {
            expCompenstationSupported = true;
        }

        Log.i(TAG, "min Exp = " + String.valueOf(params.getMinExposureCompensation()));
        Log.i(TAG, "max Exp = " + String.valueOf(params.getMaxExposureCompensation()));
        supportedSizes = params.getSupportedPreviewSizes();
        Camera.Size sz = supportedSizes.get(0);

        for (int i = 0; i < supportedSizes.size(); i++) {
            sz = supportedSizes.get(i);
            Log.i(TAG, "width = " + String.valueOf(sz.width));
            if (sz.width == previewWidth && sz.height == previewHeight) {
                isSizeSupported = true;
                break;
            }
        }

        if (isSizeSupported) {
            params.setPreviewSize(previewWidth, previewHeight);
        } else {
            Log.e(TAG, "Preview Size not supported");
            Log.i(TAG, "New Preview Size = (" + sz.width + "," + sz.height
                    + ")");
            previewWidth = sz.width;
            previewHeight = sz.height;
            params.setPreviewSize(sz.width, sz.height);
        }

        // Set Focus mode
		/*
		List<String> fmodes = params.getSupportedFocusModes();

		if (fmodes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
		} else if (fmodes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		} else if (fmodes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
		} */

		/*
		 * if (this.params.isZoomSupported()) {
		 * this.params.setZoom(this.currentZoomLevel); }
		 */
        try {
            mCamera.setParameters(params);
        } catch (RuntimeException e) {
            camId = 1;
            if (cameraId == 0) {
                changeCam(camId);
                Toast.makeText(pact, "Unable to initialize front camera",
                        Toast.LENGTH_SHORT).show();
                return camId;
            }

        }
        mCamera.startPreview();

        try {
            mCamera.setPreviewDisplay(sHolder);

            // Had to move it here. Otherwise it stopped focusing when capture button
            // was pressed on Nexus 7. Weird issue. I thought this might happen with
            // other devices too
            mCamera.setPreviewCallbackWithBuffer(this);
            int expectedBytes = previewWidth * previewHeight *
                    ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            for (int i=0; i < 4; i++) {
                mCamera.addCallbackBuffer(new byte[expectedBytes]);
            }
            mState = STATE_PREVIEW;
            //mCamera.setPreviewCallback(this);
        } catch (IOException e) {
            mCamera.release();
            mCamera = null;
            return -1;
        }
        this.pact.changeAlphaFromAngle(expAngle);
        Log.i(TAG, "changeAlphaFromAngle - " + String.valueOf(expAngle));
        isCamOpen = 1;
        return isCamOpen;
    }
    public int isCamOpen() {
        return isCamOpen;
    }

    public void resetSize(int[] size) {
        previewWidth = size[0];
        previewHeight = size[1];
        this.openCamera(camId);
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            mState = STATE_OFF;
        }
        isCamOpen = 0;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (data == null) {
            Log.i(TAG, "data is null");
            return;
        }

        int expectedBytes = previewWidth * previewHeight *
                ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;

        if (expectedBytes != data.length) {
            Log.e(TAG, "Mismatched size of buffer! Expected ");

            mState = STATE_NO_CALLBACKS;
            mCamera.setPreviewCallbackWithBuffer(null);
            return;
        }

        if (mProcessInProgress || mState == STATE_PROCESS_IN_PROGRESS || mState == STATE_PREVIEW_PICTURE_IN_PROGRESS) {
            mCamera.addCallbackBuffer(data);
            return;
        }

        if (mState == STATE_PROCESS) {
            mProcessInProgress = true;
            processDataTask = new ProcessPreviewDataTask();
            processDataTask.execute(data);

        } else if (mState == STATE_PREVIEW_PICTURE) {
            Log.i(TAG, "calling PictureTask");
            getPictureTask = new GetPictureTask();
            getPictureTask.execute(data);
        } else {
            mCamera.addCallbackBuffer(data);
            return;
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        sHolder = holder;

        isCamOpen = openCamera(camId);
        if (isCamOpen == -1) {
            this.pact.closeDueToCameraUnavailabilty();
        }
		/*
		try {
			mCamera.autoFocus(new AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean arg0, Camera arg1) {
					Log.v("Focus", "Got the focus");
					mCamera.cancelAutoFocus();
					isFocusable = true;

				}
			});
		} catch (RuntimeException e) {
			isFocusable = false;
			Log.v("Focus", "Could not focus");
		}*/
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();

    }

    /**
     * Called from ParallelActivity to stop processing when the shoot button is pressed
     * to start shooting.
     * Create a float buffer.
     * change processing and camera preview status.
     */
    public int startProcessing() {
        floatpixels = null;
        //logHeap();
		/*
		try {
			mCamera.autoFocus(null);
		} catch (NullPointerException e) {
			Log.i(TAG, "autofocus null gave error");
		}*/

        // set callbackbuffer
		/*
		mCamera.setPreviewCallbackWithBuffer(this);
        int expectedBytes = previewWidth * previewHeight *
                ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
        for (int i=0; i < 4; i++) {
            mCamera.addCallbackBuffer(new byte[expectedBytes]);
        }*/

        mProcessingFirstFrame = true;
        System.gc();
        Log.i(TAG, "GC done");
        //logHeap();
        floatpixels = new float[previewWidth * previewHeight * 3];
        Log.i(TAG, "floatpixels craeted");
        //logHeap();
        changeProcessingStatus(true);
        mState = STATE_PROCESS;
        return 1;
    }

    /**
     * Called from ParallelActivity to stop processing when the shoot button is pressed.
     * if processing is still undergoing, get some time to let it finish.
     * change processing and camera preview status.
     */
    public int stopProcessing() {
        changeProcessingStatus(false);
        // check if process thread has finished. If not wait 200 msec
        // Else thread crashes.
        if (mProcessInProgress) {
            Log.i(TAG, "processing thread still active.");
            try {
                Log.i(TAG, "waiting  200 msec");
                processDataTask.get(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
        mCamera.setPreviewCallbackWithBuffer(null);
        System.gc(); // collect garbage. possibly raw callback data
        return 1;
    }

    /**
     * Called from ParallelActivity to pause processing when the pause button is pressed.
     * (encapsulation)
     * change state of cameraPreview.
     */
    public void pauseProcessing() {
        mState = STATE_PREVIEW;
    }

    /**
     * Called from ParallelActivity to resume processing once the resume button is pressed.
     * (encapsulation)
     * change state of cameraPreview.
     */
    public void resumeProcessing() {
        mState = STATE_PROCESS;
    }

    /**
     * Called from ParallelActivity to create final preview (why preview? Should be changed)
     * once the stop button is called (timer/manual).
     *
     * Creates a new instance of CreateBitmapTask().
     */
    public int createPreviewBitmap() {
        createBitmapTask t = new createBitmapTask();
        t.execute(floatpixels);
        return 1;
    }

    /**
     * encapsulation
     * @return camera id.
     */
    public int getCameraId() {
        return camId;
    }

    /**
     * encapsulation
     * @return preview bitmap.
     */
    public Bitmap getPreviewBitmap() {
        return bitmap;
    }

    /**
     * Called from ParallelActivity to change zoom.
     *
     * @param - float - new zoom value.
     */

    public void zoom(float m) {
        try {
            Log.v("Zoom", "Yes executed1");
            if (params.isZoomSupported()) {
                Log.i("CamPreviewActivity", "zoom = " + String.valueOf(m));
                int maxZoomLevel = params.getMaxZoom();

                float zoom = currentZoomLevel;
                zoom += m;

                currentZoomLevel = (int) zoom;

                if (currentZoomLevel > maxZoomLevel) {
                    currentZoomLevel = maxZoomLevel;
                }
                if (currentZoomLevel < 1) {
                    currentZoomLevel = 1;
                }
                Log.v("Zoom", "Yes executed2");
                params.setZoom(currentZoomLevel);
                mCamera.setParameters(params);
            }
        } catch (NullPointerException e) {
            Log.v("ZoomError", e.toString() + " " + currentZoomLevel);
        }

    }

    /**
     * Called from PreviewSurfaceView to set touch focus.
     *
     * @param - Rect - new area for auto focus
     */
    public void doTouchFocus(final Rect tfocusRect) {
        Log.i(TAG, "TouchFocus");
        try {
            final List<Camera.Area> focusList = new ArrayList<Camera.Area>();
            Camera.Area focusArea = new Camera.Area(tfocusRect, 1000);
            focusList.add(focusArea);

            Camera.Parameters para = mCamera.getParameters();
            para.setFocusAreas(focusList);
            para.setMeteringAreas(focusList);
            mCamera.setParameters(para);

            mCamera.autoFocus(myAutoFocusCallback);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Unable to autofocus");
        }
		/*
		if(sHolder.getSurface().isValid()) {
	    	  Canvas canvas = sHolder.lockCanvas();
	    	  Paint p = new Paint();
	    	  p.setStyle(Paint.Style.STROKE);
	    	  p.setStrokeWidth(2);
	    	  p.setColor(0xeed7d7d7);
	    	  if (canvas == null) {
	    		  Log.i(TAG, "canvas is null");
	    	  }
	    	  canvas.drawRGB(255, 0, 255);
	    	  //canvas.drawRect(focusRect, p);
	    	  Log.i(TAG, "drawn rect");
	    	  sHolder.unlockCanvasAndPost(canvas);
	      }*/
    }

    /**
     * AutoFocus callback
     */
    AutoFocusCallback myAutoFocusCallback = new AutoFocusCallback(){

        @Override
        public void onAutoFocus(boolean arg0, Camera arg1) {
            // TODO Auto-generated method stub
            if (arg0){
                mCamera.cancelAutoFocus();
            }
        }
    };

    /**
     * Called from ParallelActivity to change exposure compenstaion.
     *
     * @param - double - new angle value.
     *
     * Angle value ranges from -72 to +72. Normalises exposure compensation value
     * based on min and max compensation values of the camera (varies with each device)
     */
    public int changeExposureComp(double angle) {
        expAngle = -angle+72; // Wasn't getting changed when openCamera called after processing or preview
        if (expCompenstationSupported) {
            // NewValue = (((OldValue - OldMin) * (NewMax - NewMin)) / (OldMax - OldMin)) + NewMin
            expCompValue = (int)(((angle - 0)*(maxExp - minExp))/(144 - 0)) + minExp;
            //int NewValue = (int)(((alpha - 0) * (maxExp - minExp)) / (0.0005 - 0)) + minExp;
            //Log.i(TAG, "new exp value = " + String.valueOf(expCompValue));
            if (mCamera != null) {
                params.setExposureCompensation(expCompValue);
                mCamera.setParameters(params);
            }
        }
        return 1;
    }

    /**
     * Called from ParallelActivity to change alpha.
     * (encapsulation purposes)
     *
     * @param - double - new alpha value
     */
    public int changeAlpha(double alpha) {
        alphaVal = alpha;
        return 1;
    }

    /**
     * Called from ParallelActivity.
     * Changes camera.
     *
     * @param - int - camera id.
     */
    public int changeCam(int camId) {
        int success;
        success = openCamera(camId);
        this.camId = camId;
        return success;
    }

    public int changeProcessingStatus(boolean val) {
        doProcessing = val;
        return 1;
    }

    public float[] getFloatPixels() {
        return floatpixels;
    }

    public int[] getPreviewSize() {
        int[] size = new int[2];
        size[0] = previewWidth;
        size[1] = previewHeight;
        return size;
    }

    /**
     * Called from ParallelActivity when bitmap (.jpg) is required of the current scene.
     * Changes mState (Should we include encapsulation?).
     */
    public void getPictureForPreview() {
        Log.i(TAG, "getPictureForPreview");
        mState = STATE_PREVIEW_PICTURE;
        getPictureTaskFor = EXPOSURE_PREVIEW;
    }

    /**
     * Create a Runnable to process frames. No longer used.
     */
    private final Runnable doLongExposure = new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            long start = System.nanoTime();
            Log.i(TAG, "doLongExposure()");
            bProcessing = true;

            Log.i("frame = ", String.valueOf(numFrames));
            Log.i("width = ", String.valueOf(previewWidth));

            accumulate(previewWidth, previewHeight, frameData, floatpixels,
                    alphaVal);

            long elapsedTime = System.nanoTime() - start;
            Log.i("time taken", String.valueOf(elapsedTime / 1000000.0));
            bProcessing = false;
        }
    };

    /**
     * This class extends AsyncTask. Creates a thread in background to
     * process data and finally convert it to bitmap. This thread takes
     * some time as in the native part, it converts float32 image to uint8 image.
     *
     * @params - float[]
     *
     * Once the task is finished, it calls ParallelActivity.afterBitmapCreated() method.
     */
    private class createBitmapTask extends AsyncTask<float[], Void, Boolean> {

        @Override
        protected Boolean doInBackground(float[]... params) {
            Log.i(TAG, "creating bitmap in background");
            long start = System.nanoTime();
            float[] fpixels = params[0];

            pixels = new int[previewWidth * previewHeight];
            // convertScale function call
            convertScale(previewWidth, previewHeight, floatpixels, pixels);
            Log.i(TAG, "float to pixels conversion done");

            bitmap = Bitmap.createBitmap(previewWidth, previewHeight,
                    Bitmap.Config.ARGB_8888);

            bitmap.setPixels(pixels, 0, previewWidth, 0, 0, previewWidth,
                    previewHeight);


            long elapsedTime = System.nanoTime() - start;
            Log.i("time taken", String.valueOf(elapsedTime / 1000000.0));

            //Log.i(TAG, "Before freeing");
            //logHeap();
            // free float array
            fpixels = null;
            pixels = null;
            System.gc();

            //Log.i(TAG, "after freeing");
            //logHeap();
            return null;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            Log.i(TAG, "onPostExecute make bitmap");
            // ParallelCameraPreview.this.pact.onS
            pact.afterBitmapCreated();
        }

    }

    /**
     * This class extends AsyncTask. Creates a thread in background to
     * process the current frame
     *
     * @params - byte[] - data from previewCallback
     *
     * Each task processes only one frame. New instances are created and called
     * in previewCallback().
     */

    private class ProcessPreviewDataTask
            extends
            AsyncTask<byte[], Void, Boolean> {

        @Override
        protected Boolean doInBackground(byte[]... datas) {
            mState = STATE_PROCESS_IN_PROGRESS;
            Log.i(TAG, "background process started");
            byte[] data = datas[0];

            long t1 = java.lang.System.currentTimeMillis();

            accumulate(previewWidth, previewHeight, data, floatpixels, alphaVal);
            long t2 = java.lang.System.currentTimeMillis();
            Log.i(TAG, "processing time = " + String.valueOf(t2 - t1));

            mCamera.addCallbackBuffer(data);
            mProcessInProgress = false;
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mState = STATE_PROCESS;
            threadInProcess = false;
        }
		/*
		 * @Override protected void onCancelled() { //running = false; }
		 */
    }

    /**
     * This class extends AsyncTask. Creates a thread in background to
     * save current scene as .jpg.
     *
     * @params - byte[] - data from previewCallback
     *
     * Once the task is finished, it calls appropriate method of ParallelActivity
     * based on 'getPictureTaskFor' String.
     * Currently, it just calls startExposurePreviewActivity()
     */

    private class GetPictureTask extends AsyncTask<byte[], Void, Boolean> {

        @Override
        protected Boolean doInBackground(byte[]... params) {
            mState = STATE_PREVIEW_PICTURE_IN_PROGRESS;
            byte[] nvFrameData = params[0];
            YuvImage img = new YuvImage(nvFrameData, ImageFormat.NV21, previewWidth, previewHeight, null);

            File file = getDateFile();
            FileOutputStream filecon;
            try {
                filecon = new FileOutputStream(file);
                img.compressToJpeg(new Rect(0, 0, img.getWidth(), img.getHeight()), 90, filecon);
                previewBitmapFileName = file.toString();
                Log.i(TAG, "file saved " + file.toString());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.i(TAG, "onPostExecute");
            mState = STATE_PREVIEW;

            if (getPictureTaskFor == EXPOSURE_PREVIEW) {
                pact.startExposurePreviewActivity(previewBitmapFileName);
            }
        }

        /**
         * get a file with path as current time.
         * @return File
         */
        private File getDateFile() {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            Date now = new Date();
            String _path = Environment.getExternalStorageDirectory()
                    + File.separator + "Parallel" + File.separator
                    + formatter.format(now) + ".jpg";
            File file = new File(_path);
            return file;
        }

    }

    /**
     * Keep heap log.
     */
    public static void logHeap() {
        Double allocated = new Double(Debug.getNativeHeapAllocatedSize())/new Double((1048576));
        Double available = new Double(Debug.getNativeHeapSize())/1048576.0;
        Double free = new Double(Debug.getNativeHeapFreeSize())/1048576.0;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);

        Log.d(TAG, "debug. =================================");
        Log.d(TAG, "debug.heap native: allocated " + df.format(allocated)
                + "MB of " + df.format(available)
                + "MB (" + df.format(free) + "MB free)");
        Log.d(TAG, "debug.memory: allocated: " + df.format(
                new Double(Runtime.getRuntime().totalMemory()/1048576))
                + "MB of " + df.format(new Double(Runtime.getRuntime().maxMemory()/1048576))
                + "MB (" + df.format(new Double(Runtime.getRuntime().freeMemory()/1048576))
                +"MB free)");
    }

    static {
        System.loadLibrary("ParallelLongEx");
        Log.i(TAG, "Native library loaded");
    }

    public native boolean accumulate(int width, int height,
                                     byte[] NV21FrameData, float[] floatpixels, double alpha);
    public native boolean convertScale(int width, int height,
                                       float[] floatpixels, int[] pixels);
}
