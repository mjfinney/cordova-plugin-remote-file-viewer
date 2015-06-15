package com.kivra.remotefileviewer;

import java.net.URLConnection;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

import android.support.v4.content.FileProvider;
import android.content.ActivityNotFoundException;
import android.os.AsyncTask;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class RemoteFileViewer extends CordovaPlugin {

    protected static final String LOG_TAG = "RemoteFileViewer";

    private static final String UPDATE_EVENT = "update";
    private static final String ERROR_EVENT = "error";
    private static final String SUCCESS_EVENT = "success";

    private CallbackContext callbackContext;
    private static String FILE_PROVIDER_PACKAGE_ID;
    
    private static final String FILE_VIEWER = "_fileviewer";
    private static final String CONTENT_DIR = "content";

    public static enum Error {
        ENOPKG (65, "couldn't find activity for this mime type"),
        EGEN   (1, "something went wrong");

        private final int code ;
        private final String msg;

        Error(int code, String msg) {
            this.code = code;
            this.msg = msg;
        }
    }
    
    private class ViewRemoteFileTask extends AsyncTask<String, Void, File> {
        
        private Exception exception;
        private Context context;
        private String contentType;
        private int contentLength;
        private Intent intent;
            
        @Override
        protected void onPreExecute() {
            this.context = RemoteFileViewer.this.cordova.getActivity();
            intent = new Intent(Intent.ACTION_VIEW);
            RemoteFileViewer.this.sendUpdate("start", 0.0);
        }
        
        @Override
        protected File doInBackground(String... urls) {
            File dir = new File(this.context.getCacheDir(), CONTENT_DIR);
            String fileUrlString = urls[0];
            dir.mkdirs();
            File outFile = new File(dir, Uri.parse(fileUrlString).getLastPathSegment());
            try {
                URL fileUrl = new URL(fileUrlString);
                String protocol = fileUrl.getProtocol();
                URLConnection urlConnection;
                if (protocol.equals("https")) {
                    urlConnection = (HttpsURLConnection) fileUrl.openConnection();
                }
                else if (protocol.equals("http")) {
                    urlConnection = (HttpURLConnection) fileUrl.openConnection();
                }
                else {
                    throw new IOException("protocol not supported: "+protocol);
                }
                contentType = urlConnection.getContentType();
                contentLength = urlConnection.getContentLength();
                intent.setType(contentType);
                Uri contentUri = FileProvider.getUriForFile(this.context, FILE_PROVIDER_PACKAGE_ID, outFile);
                intent.setDataAndType(contentUri, contentType);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // check if there are an application available to present this filetype before finish downloading 
                if (intent.resolveActivity(context.getPackageManager()) == null) {
                    throw new ActivityNotFoundException();
                }
                InputStream is = new BufferedInputStream(urlConnection.getInputStream());
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int len = 0;
                int tot = 0;
                double ratio = 0;
                Log.d(LOG_TAG, "total: "+String.valueOf(contentLength));
                while ( (len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                    tot += len;
                    RemoteFileViewer.this.sendUpdate("downloading", (double) tot/contentLength); 
                }
                fos.close();
                return outFile;
            }
            catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(File file) {
            // map caught exceptions to predefined errors
            if (exception != null) {
                if (exception instanceof ActivityNotFoundException) {
                    RemoteFileViewer.this.sendError(RemoteFileViewer.Error.ENOPKG);
                }
                else {
                    RemoteFileViewer.this.sendError(RemoteFileViewer.Error.EGEN);
                }
            } else {
                // everything is fine, we can send away our intent to view the file
                RemoteFileViewer.this.sendSuccess();
                context.startActivity(intent);
            }
        }
    }
    
   public void sendUpdate(String status, double progress) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", UPDATE_EVENT);
            obj.put("status", status);
            obj.put("progress", progress);
            sendUpdate(obj, true, PluginResult.Status.OK);
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "JSON exception");
        }
    }
    
    public void sendSuccess() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", SUCCESS_EVENT);
            obj.put("status", "completed");
            sendUpdate(obj, true, PluginResult.Status.OK);
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "JSON exception");
        }
    }
    
    public void sendError(Error e) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", ERROR_EVENT);
            obj.put("code", e.code);
            obj.put("message", e.msg);
            sendUpdate(obj, true, PluginResult.Status.ERROR);
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "JSON exception");
        }
    }

    // Initialize
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        FILE_PROVIDER_PACKAGE_ID = cordova.getActivity().getPackageName() + ".fileprovider";
        Log.d(LOG_TAG, FILE_PROVIDER_PACKAGE_ID);
    }
    /**
     * Executes the request and returns PluginResult.
     *
     * @param action        The action to execute.
     * @param args          JSONArry of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("open".equals(action)) {
            Log.d(LOG_TAG, "opening: "+args.getString(0));
            this.callbackContext = callbackContext;
            viewFile(args.getString(0));
            return true;
        }
        return false;
    }

    private void viewFile(String url) {
        new ViewRemoteFileTask().execute(url);
    }

    private void sendUpdate (JSONObject obj, boolean keepCallback,PluginResult.Status status) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(status, obj);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
            if (!keepCallback) {
                callbackContext = null;
            }
        }
    }

}
