package com.example;

import android.os.IBinder;
import java.lang.reflect.Constructor;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IRemoteProcess;

public class ShizukuShellRunner {
    public static Process runCommand(String[] cmd) throws Exception {
        IBinder binder = Shizuku.getBinder();
        if (binder == null) {
            throw new IllegalStateException("Shizuku binder is null");
        }
        IShizukuService service = IShizukuService.Stub.asInterface(binder);
        if (service == null) {
            throw new IllegalStateException("Shizuku service is null");
        }
        IRemoteProcess remoteProcess = service.newProcess(cmd, null, null);
        if (remoteProcess == null) {
            throw new IllegalStateException("Failed to spawn remote process via Shizuku");
        }
        
        Constructor<ShizukuRemoteProcess> constructor = ShizukuRemoteProcess.class.getDeclaredConstructor(IRemoteProcess.class);
        constructor.setAccessible(true);
        return constructor.newInstance(remoteProcess);
    }
}
