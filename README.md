# cordova-plugin-remote-file-viewer

This plugin defines a global RemoteFileViewer object which can be used to download and delegate the presentation of remote files fetched over http or https. The presenting app only gets [temporary](https://developer.android.com/reference/android/support/v4/content/FileProvider.html) read conditions to the file and the file is only stored in your
app's cache.

    window.RemoteFileViewer.open(url);

## Supported platforms
- Android

## Installation

    cd $YOUR_CORDOVA_PROJECT
    cordova plugin add $PATH_TO_LOCAL_CLONE_OF_THIS_REPO

