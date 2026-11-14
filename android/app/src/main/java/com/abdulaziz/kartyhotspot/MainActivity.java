package com.abdulaziz.kartyhotspot;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                String[] ids = cameraManager.getCameraIdList();
                if (ids.length > 0) {
                    cameraId = ids[0]; // الكاميرا الخلفية
                }
            }
        } catch (CameraAccessException ignored) {}

        // ربط الجسر الأصلي للتحكم بالفلاش مباشرة من الجافاسكريبت
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new NativeTorchBridge(), "AndroidTorch");
        }
    }

    public class NativeTorchBridge {
        @JavascriptInterface
        public void setTorch(boolean enabled) {
            if (cameraManager != null && cameraId != null) {
                try {
                    cameraManager.setTorchMode(cameraId, enabled);
                    isTorchOn = enabled;
                } catch (Exception ignored) {}
            }
        }

        @JavascriptInterface
        public void toggleTorch() {
            setTorch(!isTorchOn);
        }

        @JavascriptInterface
        public boolean isSupported() {
            return cameraManager != null && cameraId != null;
        }
    }
}
