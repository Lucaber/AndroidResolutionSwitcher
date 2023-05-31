package de.lucaber.ResolutionSwitcher;

import android.content.Context;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.Display;
import android.os.Parcel;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import android.os.IBinder;

public class ResolutionTileService extends TileService {

    private Context context;
    private Tile tile;

    private Display.Mode[] availableModes;

    private int currentModeIndex = 0;

    private String name = "ResolutionTileService";
    private String LogTag = name;
    private String ResolutionPreference = "resolution_preference";

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        updateState();
        int storedMode = context.getSharedPreferences(LogTag, MODE_PRIVATE).getInt(ResolutionPreference, -1);
        if (storedMode != currentModeIndex) {
            try {
                activateMode(storedMode);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private String modeToString(Display.Mode mode) {
        return String.format(Locale.US,"%d:%d@%f", mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
    }

    private void updateState() {
        Display.Mode mode = context.getDisplay().getMode();
        availableModes = context.getDisplay().getSupportedModes();
        for (int i = 0; i < availableModes.length; i++) {
            if (mode.equals(availableModes[i])) {
                currentModeIndex = i;
            }
            Log.v(LogTag, "found mode:" + modeToString(availableModes[i]));
        }
        Log.v(LogTag, "active mode:" + modeToString(mode));
    }

    private void activateMode(int mode) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, RemoteException, NoSuchMethodException, IOException, InterruptedException {
        if (mode >= availableModes.length) {
            throw new IndexOutOfBoundsException();
        }
        Method method = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
        IBinder surfaceFlinger = (IBinder) method.invoke(null, "SurfaceFlinger");
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken("android.ui.ISurfaceComposer");
        data.writeInt(mode);
        surfaceFlinger.transact(1035, data, null, 0);

        Runtime.getRuntime().exec("/system/bin/su -c /system/bin/killall com.android.systemui").waitFor();
    }

    private void cycleModes() {
        int newMode = nextModeIndex();
        Log.v(LogTag, String.format(Locale.US,"switching mode %d %s to %d %s",
                currentModeIndex,
                modeToString(availableModes[currentModeIndex]),
                newMode,
                modeToString(availableModes[newMode])
        ));
        try {
            activateMode(newMode);
            context.getSharedPreferences(LogTag, MODE_PRIVATE).edit().putInt(ResolutionPreference, newMode).apply();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        currentModeIndex = newMode;
    }

    private int nextModeIndex() {
        Display.Mode currentMode = availableModes[currentModeIndex];
        int next = currentModeIndex;
        while(true) {
            next++;
            if (next >= availableModes.length) {
                next = 0;
            }
            if (next == currentModeIndex) {
                return currentModeIndex;
            }
            Display.Mode nextMode = availableModes[next];

            if ((currentMode.getPhysicalWidth() != nextMode.getPhysicalWidth()) &&
                    (currentMode.getPhysicalHeight() != nextMode.getPhysicalHeight()) &&
                    (currentMode.getRefreshRate() == nextMode.getRefreshRate())) {
                return next;
            }
        }
    }

    private void updateTileView() {
        Display.Mode mode = availableModes[currentModeIndex];
        tile.setContentDescription(modeToString(mode));
        tile.setSubtitle(modeToString(mode));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        updateTileView();
    }

    @Override
    public void onClick() {
        super.onClick();

        int previousMode = currentModeIndex;
        updateState();
        if (currentModeIndex != previousMode) {
            // mode changed since last change through tile
        } else {
            cycleModes();
        }

        updateTileView();
    }
}