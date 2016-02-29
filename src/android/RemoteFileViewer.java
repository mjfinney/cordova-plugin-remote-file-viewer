package com.kivra.remotefileviewer;

import java.net.URLConnection;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.support.v4.content.FileProvider;
import android.content.ActivityNotFoundException;
import android.os.AsyncTask;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.apache.cordova.file.FileUtils;

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
    private static final String DOWNLOAD_DIR = "downloaded";
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

    /**
     * Returns the MIME Type of the file by looking at file name extension in
     * the URL.
     *
     * @param url
     * @return
     */
    private static String getMimeType(String url) {
    	String mimeType = null;
    
    	String extension = MimeTypeMap.getFileExtensionFromUrl(url);
    	if (extension != null) {
    		MimeTypeMap mime = MimeTypeMap.getSingleton();
    		mimeType = mime.getMimeTypeFromExtension(extension);
    	}
    
    	System.out.println("Mime Type: " + mimeType);
    
    	return mimeType;
    }

    /*
    private static void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }
    */
    
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
            String fileUrlString = urls[0];
            File dir;
            File outFile;
            if (fileUrlString.contains("cache")) {
                //File appdir = new File(this.context.getApplicationInfo().dataDir);
                //File oldFile = new File(fileUrlString);
                //String appDir = this.context.getApplicationInfo().dataDir;
                //File oldFile = new File(appDir + '/' + fileUrlString); 

                dir = new File(this.context.getCacheDir(), CONTENT_DIR);
                outFile = new File(dir, Uri.parse(fileUrlString).getLastPathSegment());
                /*
                try{
                    copy(oldFile, outFile);
                }catch(IOException e){
                    e.printStackTrace();
                    Log.e(LOG_TAG, "Issue copying file: "+oldFile);
                    Log.e(LOG_TAG, "error: "+e);
                }
                */
            } else {
                dir = new File(this.context.getFilesDir(), DOWNLOAD_DIR);
                outFile = new File(dir, Uri.parse(fileUrlString).getLastPathSegment());
            }
            //Log.d(LOG_TAG, "fileUrlString: "+fileUrlString);
            //Log.d(LOG_TAG, "file: "+outFile);
            //Log.d(LOG_TAG, "dir: "+dir);
            try {
                contentType = getMimeType(fileUrlString);
                intent.setType(contentType);
                Uri contentUri = FileProvider.getUriForFile(this.context, FILE_PROVIDER_PACKAGE_ID, outFile);
                intent.setDataAndType(contentUri, contentType);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // check if there are an application available to present this filetype before finish downloading 
                if (intent.resolveActivity(context.getPackageManager()) == null) {
                    throw new ActivityNotFoundException();
                }
                return outFile;
            }
            catch (Exception e) {
                Log.d(LOG_TAG, "exception: "+e);
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
