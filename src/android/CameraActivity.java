package com.vgc.cordova.plugins.heartbeat;

import org.apache.cordova.CordovaActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.Toast;

public class CameraActivity extends CordovaActivity {

	private static final String TAG = "CameraActivity";
	private static final int CAMERA_REQUEST_CODE = 100;

	private int fps;
	private int seconds;
	private Camera camera;
	private ForegroundCameraPreview preview;
	private HeartBeatDetection detection;
	private byte[] buffer;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		seconds = getIntent().getExtras().getInt(HeartBeatPlugin.SECONDS_KEY);
		fps = getIntent().getExtras().getInt(HeartBeatPlugin.FPS_KEY);

		setContentView(getResources().getIdentifier("foregroundcamera",
				"layout", getPackageName()));

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			initCamera();
		}
		else {
			requestCameraPermission();
		}
	}

	private void initCamera() {
		camera = getCameraInstance();

		try {
			Camera.Parameters params = camera.getParameters();
			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
			params.setPreviewFpsRange(fps * 1000, fps * 1000);
			Size size = params.getSupportedPreviewSizes().get(
					params.getSupportedPreviewSizes().size() - 1);
			params.setPreviewSize(size.width, size.height);
			params.setPreviewFormat(ImageFormat.NV21);
			camera.setParameters(params);
			camera.setPreviewCallbackWithBuffer(previewCallback);
		} catch (Exception e) {
			Log.e(TAG, "Camera error", e);
			setResult(RESULT_CANCELED);
			finish();
			return;
		}

		LinearLayout layout = (LinearLayout) findViewById(getResources()
				.getIdentifier("camera_preview", "id", getPackageName()));
		int width = camera.getParameters().getPreviewSize().width;
		int height = camera.getParameters().getPreviewSize().height;
		buffer = new byte[getBufferSize(width, height)];
		camera.addCallbackBuffer(buffer);
		layout.setTranslationX(2000);

		detection = new HeartBeatDetection(camera);
		preview = new ForegroundCameraPreview(this, camera);
		ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layout.addView(preview, layoutParams);
	}

	public void requestCameraPermission() {
		if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
		}
		else {
			initCamera();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == CAMERA_REQUEST_CODE) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				//Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
				initCamera();
			} else {
				//Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
				CameraActivity.this.finish();
			}
		}
	}

	public static Camera getCameraInstance() {
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras == 0) {
			Log.w(TAG, "No cameras!");
			return null;
		}
		int index = 0;
		while (index < numCameras) {
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(index, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				break;
			}
			index++;
		}
		Camera camera;
		if (index < numCameras) {
			Log.i(TAG, "Opening camera #" + index);
			camera = Camera.open(index);
		} else {
			Log.i(TAG, "No camera facing back; returning camera #0");
			camera = Camera.open(0);
		}
		return camera;
	}

	@Override
	public void finish() {
		if (camera != null) {
			try {
				Camera.Parameters params = camera.getParameters();
				params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
				camera.setParameters(params);
				camera.stopPreview();
				camera.setPreviewCallback(null);
			} catch (Exception e) {
				Log.d(TAG, "Exception stopping camera: " + e.getMessage());
			}
			camera.release();
			camera = null;
		}
		super.finish();
	}

	private int getBufferSize(int width, int height) {
		return width * height * ImageFormat.getBitsPerPixel(ImageFormat.NV21);
	}

	private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

		private int count = 0;

		@Override
		public void onPreviewFrame(byte[] buffer, Camera camera) {

			detection.analyzeFrame(buffer);
			camera.addCallbackBuffer(buffer);
			count++;
			if (count == (seconds * fps)) {
				int bpm = detection.getHeartBeat(fps);
				Intent intent = new Intent();
				intent.putExtra(HeartBeatPlugin.BPM_KEY, bpm);
				CameraActivity.this.setResult(RESULT_OK, intent);
				CameraActivity.this.finish();
			}

		}
	};

}