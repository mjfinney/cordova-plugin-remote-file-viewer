# cordova-plugin-remote-file-viewer

This plugin defines a global RemoteFileViewer object which can be used to download and delegate the presentation of remote files fetched over http or https. The presenting app only gets [temporary](https://developer.android.com/reference/android/support/v4/content/FileProvider.html) read conditions to the file and the file is only stored in your
app's cache.

    window.RemoteFileViewer.open(url);

## Supported platforms
- Android

## Installation

    cd $YOUR_CORDOVA_PROJECT
    cordova plugin add $PATH_TO_LOCAL_CLONE_OF_THIS_REPO

## Usage


    // Example function to view a remote file and log
    // progress in the console:
    
    function viewRemoteFile(url) {
        var ref = window.RemoteFileViewer.open(url);
        function onEvent (e) {
            var msg = JSON.stringify(e);
            console.log(msg);
        }
        ref.addEventListener('update', onEvent);
        ref.addEventListener('success', onEvent);
        ref.addEventListener('error', onEvent);
    };

    // Example output in sucess case:

    index.js:38 {"type":"update","status":"start","progress":0}
    index.js:38 {"type":"update","status":"downloading","progress":0.008504847763225038}
    index.js:38 {"type":"update","status":"downloading","progress":0.012875824068203725}

    index.js:38 { ... }

    index.js:38 {"type":"update","status":"downloading","progress":0.9881808388357121}
    index.js:38 {"type":"update","status":"downloading","progress":0.9987371589684908}
    index.js:38 {"type":"update","status":"downloading","progress":1}
    index.js:38 {"type":"success","status":"completed"}

    // Example output if no app was found to open application:
    
    index.js:38 {"type":"update","status":"start","progress":0}
    index.js:38 {"type":"error","code":65,"message":"couldn't find activity for this mime type"}


