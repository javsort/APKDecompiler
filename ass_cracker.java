// Flip to true to see the chatty per-call bypass logs (file/property access).
var VERBOSE = false;

Java.perform(function() {
    try {
        var Thread = Java.use("java.lang.Thread");
        var ThreadGroup = Java.use("java.lang.ThreadGroup");

        // Catch unhandled exceptions on custom threads to prevent app termination
        var originalDispatch = Thread.dispatchUncaughtException;
        Thread.dispatchUncaughtException.implementation = function(e) {
            var exName = e.$className;
            if (exName && exName.indexOf("ArithmeticException") !== -1) {
                console.log("[BYPASS] Suppressed ArithmeticException crash: " + e.getMessage());
                return; // Ignore and keep thread alive
            }
            // Survive the rebuilt-app OnUnhandledKeyEventListener class-resolution crash
            var msg = "";
            try { msg = "" + e.getMessage(); } catch (ig) {}
            if (msg.indexOf("OnUnhandledKeyEventListener") !== -1) {
                console.log("[BYPASS] Suppressed class-resolution crash: " + msg);
                return; // keep the process alive so tracing can continue
            }
            return originalDispatch.call(this, e);
        };
    } catch (err) {
        console.log("[ERROR] Thread exception hook failed: " + err);
    }
});

Java.perform(function() {
    console.log("[AUABF] Initializing comprehensive Anti-VM and Bypass script...");

    // 1. Repair OnUnhandledKeyEventListener class resolution.
    //    Rebuilt app requests a descriptor-form name loadClass can't resolve; retry with the real class.
    try {
        var ClassLoader = Java.use("java.lang.ClassLoader");
        ClassLoader.loadClass.overload('java.lang.String', 'boolean').implementation = function(name, resolve) {
            try {
                return this.loadClass(name, resolve);
            } catch (e) {
                if (name && name.indexOf("OnUnhandledKeyEventListener") !== -1) {
                    console.log("[BYPASS] Repairing class load for: " + name);
                    try {
                        return this.loadClass("android.view.View$OnUnhandledKeyEventListener", resolve);
                    } catch (e2) {
                        return Java.use("android.view.View$OnUnhandledKeyEventListener").class;
                    }
                }
                throw e;
            }
        };
    } catch(e) {}

    // 2. Spoof Build Properties (Anti-VM Check Fixes)
    var Build = Java.use("android.os.Build");
    Build.FINGERPRINT.value = "google/blueline/blueline:10/QP1A.191005.007.A3/5972272:user/release-keys";
    Build.MODEL.value = "Pixel 3";
    Build.MANUFACTURER.value = "Google";
    Build.PRODUCT.value = "blueline";
    Build.DEVICE.value = "blueline";
    Build.HARDWARE.value = "qcom";
    Build.BRAND.value = "google";
    Build.TAGS.value = "release-keys";
    Build.TYPE.value = "user";

    try {
        Build.SERIAL.value = "123456789ABCDEF";
    } catch(e) {}

    // 3. System Properties check bypass (ro.kernel.qemu, ro.secure, etc.)
    try {
        var SystemProperties = Java.use("android.os.SystemProperties");
        SystemProperties.get.overload('java.lang.String').implementation = function(key) {
            if (key === "ro.kernel.qemu" || key === "ro.hardware.qemu") {
                console.log("[BYPASS-VM] SystemProperties.get: " + key + " -> 0/false");
                return "0";
            }
            if (key === "ro.secure" || key === "ro.debuggable") {
                return "0";
            }
            if (key === "ro.build.tags") {
                return "release-keys";
            }
            return this.get(key);
        };
    } catch(e) {}

    // 4. File.exists check for emulator/root artifacts
    var File = Java.use("java.io.File");
    File.exists.implementation = function() {
        var path = this.getAbsolutePath();
        var dangerous_paths = [
            "/su", "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "goldfish", "qemu", "geny", "nox", "bluestacks"
        ];
        for (var i = 0; i < dangerous_paths.length; i++) {
            if (path.indexOf(dangerous_paths[i]) != -1) {
                return false;
            }
        }
        return this.exists();
    };

    console.log("[AUABF] ✓ All Anti-VM & Support patches loaded!");
});

Java.perform(function() {
    console.log("[AUABF] Script starting...");

    var File = Java.use("java.io.File");
    File.exists.implementation = function() {
        var path = this.getAbsolutePath();
        var dangerous_paths = [
            "/su", "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/system/etc/init.d/99SuperSUDaemon",
            "/dev/com.koushikdutta.superuser.daemon/", "/system/xbin/daemonsu",
            "com.koushikdutta.superuser", "com.thirdparty.superuser",
            "eu.chainfire.supersu", "com.noshufou.android.su"
        ];

        for (var i = 0; i < dangerous_paths.length; i++) {
            if (path.indexOf(dangerous_paths[i]) != -1) {
                console.log("[BYPASS-ROOT] File.exists(): " + path + " -> false");
                return false;
            }
        }
        return this.exists();
    };

    var Runtime = Java.use("java.lang.Runtime");
    var ProcessBuilder = Java.use("java.lang.ProcessBuilder");
    var Process = Java.use("java.lang.Process");

    function createFakeProcess() {
        var ByteArrayInputStream = Java.use("java.io.ByteArrayInputStream");
        var ByteArrayOutputStream = Java.use("java.io.ByteArrayOutputStream");

        var fakeProcess = Process.$new();

        fakeProcess.getInputStream.implementation = function() {
            return ByteArrayInputStream.$new([]);
        };

        fakeProcess.getOutputStream.implementation = function() {
            return ByteArrayOutputStream.$new();
        };

        fakeProcess.getErrorStream.implementation = function() {
            return ByteArrayInputStream.$new([]);
        };

        fakeProcess.waitFor.implementation = function() {
            return 0;
        };

        fakeProcess.exitValue.implementation = function() {
            return 0;
        };

        fakeProcess.destroy.implementation = function() {
        };

        return fakeProcess;
    }

    Runtime.exec.overload('java.lang.String').implementation = function(cmd) {
        console.log("[BYPASS-ROOT] Runtime.exec: " + cmd);
        if (cmd.indexOf("su") != -1 || cmd.indexOf("which") != -1) {
            console.log("[BYPASS-ROOT] Command blocked! Returning fake process.");
            return createFakeProcess();
        }
        return this.exec(cmd);
    };

    Runtime.exec.overload('[Ljava.lang.String;').implementation = function(cmds) {
        var cmd = cmds.join(" ");
        console.log("[BYPASS-ROOT] Runtime.exec[]: " + cmd);
        if (cmd.indexOf("su") != -1 || cmd.indexOf("which") != -1) {
            console.log("[BYPASS-ROOT] Command blocked! Returning fake process.");
            return createFakeProcess();
        }
        return this.exec(cmds);
    };

    Runtime.exec.overload('java.lang.String', '[Ljava.lang.String;').implementation = function(cmd, env) {
        console.log("[BYPASS-ROOT] Runtime.exec with env: " + cmd);
        if (cmd.indexOf("su") != -1 || cmd.indexOf("which") != -1) {
            console.log("[BYPASS-ROOT] Command blocked! Returning fake process.");
            return createFakeProcess();
        }
        return this.exec(cmd, env);
    };

    Runtime.exec.overload('[Ljava.lang.String;', '[Ljava.lang.String;').implementation = function(cmds, env) {
        var cmd = cmds.join(" ");
        console.log("[BYPASS-ROOT] Runtime.exec[] with env: " + cmd);
        if (cmd.indexOf("su") != -1 || cmd.indexOf("which") != -1) {
            console.log("[BYPASS-ROOT] Command blocked! Returning fake process.");
            return createFakeProcess();
        }
        return this.exec(cmds, env);
    };

    ProcessBuilder.start.implementation = function() {
        var cmd = this.command();
        console.log("[BYPASS-ROOT] ProcessBuilder.start: " + cmd);
        if (cmd.toString().indexOf("su") != -1) {
            console.log("[BYPASS-ROOT] Process blocked! Returning fake process.");
            return createFakeProcess();
        }
        return this.start();
    };

    var Build = Java.use("android.os.Build");
    Build.TAGS.value = "release-keys";

    try {
        var SystemProperties = Java.use("android.os.SystemProperties");
        SystemProperties.get.overload('java.lang.String').implementation = function(key) {
            if (VERBOSE) console.log("[BYPASS-ROOT] SystemProperties.get: " + key);
            if (key == "ro.debuggable" || key == "ro.secure") {
                return "0";
            }
            if (key == "ro.build.tags") {
                return "release-keys";
            }
            return this.get(key);
        };
    } catch(e) {
        console.log("[ERROR] SystemProperties hook failed: " + e);
    }

    try {
        var PackageManager = Java.use("android.app.ApplicationPackageManager");
        PackageManager.getPackageInfo.overload('java.lang.String', 'int').implementation = function(pkg, flags) {
            var dangerous_packages = [
                "com.koushikdutta.superuser", "com.thirdparty.superuser",
                "eu.chainfire.supersu", "com.noshufou.android.su",
                "com.zachspong.temprootremovejb", "com.ramdroid.appquarantine",
                "com.koushikdutta.rommanager", "com.koushikdutta.rommanager.license",
                "com.dimonvideo.luckypatcher", "com.chelpus.lackypatch",
                "com.ramdroid.appquarantine", "com.ramdroid.appquarantinepro"
            ];

            if (dangerous_packages.indexOf(pkg) != -1) {
                console.log("[BYPASS-ROOT] Package hidden: " + pkg);
                var NameNotFoundException = Java.use("android.content.pm.PackageManager$NameNotFoundException");
                throw NameNotFoundException.$new(pkg);
            }
            return this.getPackageInfo(pkg, flags);
        };
    } catch (e) {
        console.log("[ERROR] PackageManager hook failed: " + e);
    }

    try {
        var UnixFileSystem = Java.use("java.io.UnixFileSystem");
        UnixFileSystem.checkAccess.implementation = function(file, access) {
            var path = file.toString();
            if (VERBOSE) console.log("[BYPASS-ROOT] UnixFileSystem.checkAccess " + path);

            var dangerous_paths = [
                "/su", "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
                "/system/app/Superuser.apk", "/vendor/bin/su", "/cache/su", "/data/su"
            ];

            for (var i = 0; i < dangerous_paths.length; i++) {
                if (path.indexOf(dangerous_paths[i]) != -1) {
                    console.log("[BYPASS-ROOT] Access denied: " + path);
                    return false;
                }
            }

            return this.checkAccess(file, access);
        };
    } catch(e) {
        console.log("[ERROR] UnixFileSystem hook failed: " + e);
    }

    try {
        var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');
        TrustManagerImpl.verifyChain.implementation = function(untrustedChain, trustAnchorChain, host, clientAuth, ocspData, tlsSctData) {
            console.log('[BYPASS-SSL] TrustManagerImpl.verifyChain bypassed');
            return untrustedChain;
        };
    } catch(e) {
        console.log('[ERROR] TrustManagerImpl hook failed: ' + e);
    }

    try {
        var TrustManagerImpl = Java.use('com.android.org.conscrypt.TrustManagerImpl');
        TrustManagerImpl.checkTrustedRecursive.implementation = function(certs, host, clientAuth, untrustedChain, trustAnchorChain, used) {
            console.log('[BYPASS-SSL] TrustManagerImpl.checkTrustedRecursive bypassed');
            return Java.use('java.util.ArrayList').$new();
        };
    } catch(e) {
        console.log('[ERROR] checkTrustedRecursive hook failed: ' + e);
    }

    try {
        var TelephonyManager = Java.use('android.telephony.TelephonyManager');

        TelephonyManager.getNetworkOperatorName.implementation = function() {
            console.log('[BYPASS-EMU] TelephonyManager.getNetworkOperatorName -> T-Mobile');
            return "T-Mobile";
        };

        TelephonyManager.getSimOperatorName.implementation = function() {
            console.log('[BYPASS-EMU] TelephonyManager.getSimOperatorName -> T-Mobile');
            return "T-Mobile";
        };

        TelephonyManager.getLine1Number.implementation = function() {
            console.log('[BYPASS-EMU] TelephonyManager.getLine1Number -> +1234567890');
            return "+1234567890";
        };
    } catch(e) {
        console.log('[ERROR] TelephonyManager hook failed: ' + e);
    }

    try {
        var Debug = Java.use('android.os.Debug');
        Debug.isDebuggerConnected.implementation = function() {
            console.log('[BYPASS-DEBUG] Debug.isDebuggerConnected -> false');
            return false;
        };

        Debug.waitingForDebugger.implementation = function() {
            console.log('[BYPASS-DEBUG] Debug.waitingForDebugger -> false');
            return false;
        };
    } catch(e) {
        console.log('[ERROR] Debug hook failed: ' + e);
    }

    console.log("[AUABF] ✓ All bypasses loaded successfully!");
});

// ============================================================
// App logging capture: surface the app's own android.util.Log output
// ============================================================
Java.perform(function() {
    try {
        var Log = Java.use("android.util.Log");
        var levels = ["v", "d", "i", "w", "e", "wtf"];

        levels.forEach(function(lvl) {
            var L = lvl.toUpperCase();
            // (String tag, String msg)
            try {
                Log[lvl].overload("java.lang.String", "java.lang.String").implementation = function(tag, msg) {
                    console.log("[APP-LOG/" + L + "] " + tag + ": " + msg);
                    return this[lvl](tag, msg);
                };
            } catch (e) {}
            // (String tag, String msg, Throwable tr)
            try {
                Log[lvl].overload("java.lang.String", "java.lang.String", "java.lang.Throwable").implementation = function(tag, msg, tr) {
                    console.log("[APP-LOG/" + L + "] " + tag + ": " + msg + "  |EX| " + tr);
                    return this[lvl](tag, msg, tr);
                };
            } catch (e) {}
        });

        // Log.w(String tag, Throwable tr)
        try {
            Log.w.overload("java.lang.String", "java.lang.Throwable").implementation = function(tag, tr) {
                console.log("[APP-LOG/W] " + tag + ": " + tr);
                return this.w(tag, tr);
            };
        } catch (e) {}

        // Underlying Log.println(int priority, String tag, String msg)
        try {
            Log.println.overload("int", "java.lang.String", "java.lang.String").implementation = function(prio, tag, msg) {
                console.log("[APP-LOG/" + prio + "] " + tag + ": " + msg);
                return this.println(prio, tag, msg);
            };
        } catch (e) {}

        console.log("[APP-LOG] android.util.Log hooks installed.");
    } catch (e) {
        console.log("[APP-LOG] Failed to hook Log: " + e);
    }
});

// ============================================================
// Method tracer — surface the app's own (non-UI) function calls.
// Enable with TRACE=true; narrow TRACE_INCLUDE to a subpackage to cut noise.
// ============================================================
var TRACE = false;
var TRACE_INCLUDE = ["pt.sibs."];                          // class-name prefixes to trace
var TRACE_EXCLUDE = [".ui.", ".view", ".widget",           // skip UI + generated noise
                     "databinding", "BuildConfig", "R$", "$$"];
var TRACE_SKIP_METHODS = { "toString": 1, "hashCode": 1, "equals": 1 };
var _tracedClasses = {};

function _traceMethod(clazz, className, methodName) {
    var overloads;
    try { overloads = clazz[methodName].overloads; } catch (e) { return; }
    overloads.forEach(function(ov) {
        try {
            ov.implementation = function() {
                var a = [];
                for (var i = 0; i < arguments.length; i++) {
                    try { a.push(arguments[i] === null ? "null" : ("" + arguments[i])); }
                    catch (e) { a.push("<?>"); }
                }
                console.log("[TRACE] " + className + "." + methodName + "(" + a.join(", ") + ")");
                var ret = this[methodName].apply(this, arguments);
                if (ret !== undefined && ret !== null) console.log("[TRACE]   " + className + "." + methodName + " = " + ret);
                return ret;
            };
        } catch (e) {}
    });
}

function _traceClass(className) {
    if (_tracedClasses[className]) return;
    _tracedClasses[className] = true;
    var clazz;
    try { clazz = Java.use(className); } catch (e) { return; }
    var methods;
    try { methods = clazz.class.getDeclaredMethods(); } catch (e) { return; }
    var done = {};
    methods.forEach(function(m) {
        var mn = m.getName();
        if (done[mn] || TRACE_SKIP_METHODS[mn] || mn.indexOf("access$") === 0) return;
        done[mn] = true;
        _traceMethod(clazz, className, mn);
    });
    console.log("[TRACE] hooked class " + className);
}

function _scanAndTrace() {
    if (!TRACE) return;
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            var k, inc = false;
            for (k = 0; k < TRACE_INCLUDE.length; k++) { if (name.indexOf(TRACE_INCLUDE[k]) === 0) { inc = true; break; } }
            if (!inc) return;
            for (k = 0; k < TRACE_EXCLUDE.length; k++) { if (name.indexOf(TRACE_EXCLUDE[k]) !== -1) return; }
            _traceClass(name);
        },
        onComplete: function() {}
    });
}

if (TRACE) {
    Java.perform(function() {
        console.log("[TRACE] scanning app classes...");
        _scanAndTrace();
        setTimeout(_scanAndTrace, 5000);    // catch classes loaded after startup
        setTimeout(_scanAndTrace, 15000);
    });
}

Java.perform(function() {
    try {
        var SslSettings = Java.use("pt.sibs.xplatform.sdk.modules.security.helpers.SslSettings");
        var Log = Java.use("android.util.Log");
        var Exception = Java.use("java.lang.Exception");

        console.log("[+] Successfully loaded SslSettings target class.");

        // Utility function to get call stack trace
        function getStackTrace() {
            return Log.getStackTraceString(Exception.$new());
        }

        // Get all methods declared in the class
        var methods = SslSettings.class.getDeclaredMethods();

        methods.forEach(function(method) {
            var methodName = method.getName();
            var overloads = SslSettings[methodName].overloads;

            overloads.forEach(function(overload) {
                overload.implementation = function() {
                    console.log("\n[=== SSLSettings Called: " + methodName + " ===]");

                    // Log arguments
                    for (var i = 0; i < arguments.length; i++) {
                        console.log("  [-] Arg[" + i + "]: " + arguments[i]);
                    }

                    // Print stack trace to trace callers
                    console.log("  [-] Call Stack:\n" + getStackTrace());

                    // Execute original method
                    var result = overload.apply(this, arguments);

                    console.log("  [+] Return Value: " + result);
                    return result;
                };
            });
        });

    } catch (err) {
        console.error("[-] Error hooking SslSettings: " + err.stack);
    }
});