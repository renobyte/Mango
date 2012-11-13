package net.leetsoft.mangareader.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import net.leetsoft.mangareader.Mango;

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Mango.log("BootReceiver", "onReceive! " + intent.getAction());

        if (!Mango.getSharedPreferences().getBoolean("notifierEnabled", false))
        {
            Mango.log("BootReceiver", "Exiting because notifier is disabled.");
            System.exit(0);
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            NotifierService.scheduleOnBoot(context);
    }
}
