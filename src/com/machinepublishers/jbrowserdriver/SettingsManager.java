/* 
 * jBrowserDriver (TM)
 * Copyright (C) 2014-2016 Machine Publishers, LLC
 * 
 * Sales and support: ops@machinepublishers.com
 * Updates: https://github.com/MachinePublishers/jBrowserDriver
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.machinepublishers.jbrowserdriver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.machinepublishers.jbrowserdriver.AppThread.Pause;
import com.machinepublishers.jbrowserdriver.AppThread.Sync;

import javafx.application.Application;
import javafx.embed.swing.JFXPanel;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

class SettingsManager {
  private static final AtomicReference<Settings> settings = new AtomicReference<Settings>();

  static Settings settings() {
    return settings.get();
  }

  static void register(final Settings settings) {
    SettingsManager.settings.set(settings);
    if (settings != null) {
      LogsServer.updateSettings();
      StreamConnection.updateSettings();

      System.setProperty("quantum.multithreaded", "false");
      System.setProperty("prism.vsync", "false");
      System.setProperty("javafx.animation.pulse", Integer.toString(JBrowserDriverServer.PAINT_HZ));
      System.setProperty("quantum.singlethreaded", "true");
      System.setProperty("prism.threadcheck", "false");
      System.setProperty("prism.dirtyopts", "false");
      System.setProperty("prism.cacheshapes", "false");
      System.setProperty("prism.primtextures", "false");
      System.setProperty("prism.shutdownHook", "false");
      System.setProperty("prism.disableRegionCaching", "true");
      if (settings.headless()) {
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.allowhidpi", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("prism.maxvram", Long.toString(1024 * 1024 * 4));
        try {
          Class<?> platformFactory = Class.forName("com.sun.glass.ui.PlatformFactory");
          Field field = platformFactory.getDeclaredField("instance");
          field.setAccessible(true);
          field.set(platformFactory, Class.forName(
              "com.sun.glass.ui.monocle.MonoclePlatformFactory").newInstance());

          platformFactory = Class.forName("com.sun.glass.ui.monocle.NativePlatformFactory");
          field = platformFactory.getDeclaredField("platform");
          field.setAccessible(true);
          Constructor headlessPlatform = Class.forName("com.sun.glass.ui.monocle.HeadlessPlatform").getDeclaredConstructor();
          headlessPlatform.setAccessible(true);
          field.set(platformFactory, headlessPlatform.newInstance());
        } catch (Throwable t) {
          Logs.fatal(t);
        }
      } else {
        new JFXPanel();
      }
    }
  }

  static void register(
      final AtomicReference<Stage> stage,
      final AtomicReference<WebView> view) {
    ProxyAuth.add(settings.get().proxy());
    if (settings().headless() &&
        com.sun.glass.ui.Application.GetApplication() == null) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            Application.launch(App.class,
                new String[] {
                    Integer.toString(settings.get().screenWidth()),
                    Integer.toString(settings.get().screenHeight()),
                    Boolean.toString(settings().headless()) });
          } catch (Throwable t) {
            LogsServer.instance().exception(t);
          }
        }
      }).start();
    } else {
      final App app = new App();
      app.init(
          settings.get().screenWidth(), settings.get().screenHeight(),
          settings().headless());
      AppThread.exec(Pause.NONE, new AtomicInteger(-1), new Sync<Object>() {
        public Object perform() {
          try {
            app.start();
          } catch (Throwable t) {
            LogsServer.instance().exception(t);
          }
          return null;
        }
      });
    }
    stage.set(App.getStage());
    view.set(App.getView());
  }
}
