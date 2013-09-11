SocketJS
========

Sockets for the browser

###Packaging

To get into a useable state on my system, the following commands are performed:

    javac -cp "path\to\plugin.jar" SocketJS.java
    jar cvfm SocketJS.jar manifest.mf *.class
    jarsigner -keystore <path\to\keystore> SocketJS.jar <alias>

For more information on how to do step three, visit [this page](https://www.owasp.org/index.php/Signing_jar_files_with_jarsigner)

###Files included:

*  SocketJS.java: The source for the java backend
*  manifest.mf: In theory this does something to allow the plugin to make sockets easier
*  SocketJS.js: The javascript interface to the java plugin
*  SocketJS.jar: A prepackaged version of the provided SocketJS.java
*  SocketJS_applet.jnlp: Some thing that has to exist to make the plugin run in a browser or something I don't really know
*  example.htm: Shows how the thing is embedded. You can play with it in the browser console. Not sure if it even still works to be honest, I made it a few versions back.
     Also, you need to set your browser to allow the thing to run etc. If you look at SocketJS.js it should be somewhat self explanatory.
*  LICENCE: The licence
*  README.md: This file

###Example usage:

    var s, fd;
    (function(){
      var connect = function(id, success) {
        console.log(success?"connected":"connection failed");
        if (success) {
          fd = id;
          s = SocketJS.sendline_fn(id);
          s("GET / HTTP/1.1\r\nHost: www.google.com\r\n");
        }
      };
      var data = function(data) {
        console.log(data);
      };
      var disconnect = function() {
        console.log("Connection: close");
      }
      SocketJS.connect({
        host: "www.google.com",
        port: "80",
        connect: connect,
        data: data,
        disconnect: disconnect
      });
    })();

###TODO

Currently, SocketJS only reads lines, not binary data, and returns them to you in a callback that is called whenever data is received.

In the future, a manual read mode will be implemented, where the applet does not return data as it is received, but only when asked for.
Data will still be returned via the callback, but you can alternate between readline and reading x bytes of possibly binary data.

How binary data will be passed between java and js has not been determined, but likely data will need to be serialized/encoded in some way.
