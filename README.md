# XIntent  

An Intent traffic monitoring tool.  

## Purpose  

1. App security testing or bug hunting.  
2. Intent IPC mechanism learning.  
3. App keep-alive behavior monitoring.  

## Compatibility  

This module supports Android **9+** (for now)  

## Features  

1. Capture Intent communication and print its traffic data well.  
2. Monitor the start and termination of App process.  
3. Store the above records persistently.  
4. Support dump log.zip to module external storage.  

## Screenshots  

![app](/images/app.png)  

![log](/images/logcat.png)  

## Requirements  

- Rooted Device  
- ro.build.version.sdk >= 28  
- Xposed Framework Installed  

## Tested  

| ROM                    | Magisk | XP Framework      |
|------------------------|--------|-------------------|
| ColorOS 7 (Android 10) | 22.1   | EdXposed v0.5.2.2 |
| MIUI 12.5 (Android 10) | 23.0   | LSPosed v1.5.3    |
| AOSP (Android 11)      | 23.0   | LSPosed v1.5.4    |

## Issues  

https://github.com/2Y2s1mple/xintent/issues  
