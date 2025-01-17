/*
 * This file is part of FragPipe.
 *
 * FragPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FragPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FragPipe.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dmtavt.fragpipe.tools.fragger;

import com.dmtavt.fragpipe.Fragpipe;
import com.dmtavt.fragpipe.api.Bus;
import com.dmtavt.fragpipe.api.VersionFetcher;
import com.dmtavt.fragpipe.exceptions.ValidationException;
import com.dmtavt.fragpipe.messages.MessageMsfraggerUpdateAvailable;
import com.dmtavt.fragpipe.messages.NoteConfigMsfragger;
import com.github.chhh.utils.StringUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Msfragger {

  private static final Logger log = LoggerFactory.getLogger(Msfragger.class);

  public static Version getVersion(Path jar) throws Exception {
    // only validate Fragger version if the current Java version is 1.9 or higher
    Version test;
    if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_9)) {
      // we can't test fragger binary version when java version is less than 1.9
      throw new ValidationException("MSFragger requires Java 9+, can't check version without it.");
    }

    // get the version reported by the current executable
    test = testJar(jar.toString());
    if (!test.isVersionParsed) {
      throw new ValidationException("Could not get version info with given jar: " + jar);
    }
    return test;
  }


  public static void checkUpdates(NoteConfigMsfragger m) {
    final MsfraggerVerCmp vc = new MsfraggerVerCmp();
    final String verLocal = m.version;
    Thread t = new Thread(() -> {

      MsfraggerVersionFetcherServer vfServer = new MsfraggerVersionFetcherServer();
      MsfraggerVersionFetcherLocal vfLocal = new MsfraggerVersionFetcherLocal();
      List<VersionFetcher> verFetchers = Arrays.asList(vfServer, vfLocal);
      for (final VersionFetcher vf : verFetchers) {
        if (vf == null) {
          continue;
        }
        try {
          final String verUpdated = vf.fetchVersion();
          if (StringUtils.isNullOrWhitespace(verUpdated)) {
            continue;
          }
          // we got a non-empty version from some version fetcher
          if (vc.compare(verLocal, verUpdated) >= 0) {
            continue; // our version is same or newer
          }
          // local version is older than the fetched version
          String url = vf.getDownloadUrl();
          final String manualDownloadUrl = !StringUtils.isNullOrWhitespace(url) ? url : vfLocal.getDownloadUrl();
          Bus.post(new MessageMsfraggerUpdateAvailable(verUpdated, manualDownloadUrl));
          break;
        } catch (Exception ex) {
          // no biggie
          log.warn("Something happened while checking for MSFragger updates");
        }
      }
    });
    t.start();
  }

  private static Version testJar(String jarPath) throws Exception {
    String verStr = null;
    boolean isVersionParsed = false;
    List<Pattern> regexs = Arrays.asList(MsfraggerVerCmp.regexNewScheme1, MsfraggerVerCmp.regexOldScheme1); // New scheme first because most people are using the new version.

    for (Pattern re : regexs) {
      Matcher m = re.matcher(jarPath);
      if (m.find()) {
        isVersionParsed = true;
        verStr = m.group(2);
        break;
      }
    }

    if (!isVersionParsed) {
      ProcessBuilder pb = new ProcessBuilder(Fragpipe.getBinJava(), "-jar", jarPath);
      pb.redirectErrorStream(true);
      Process pr = pb.start();
      pr.waitFor();
      try (BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
        String line;
        while ((line = in.readLine()) != null) {
          for (Pattern re : regexs) {
            Matcher m = re.matcher(line);
            if (m.find()) {
              isVersionParsed = true;
              verStr = m.group(2);
              break;
            }
          }
          if (isVersionParsed) {
            break;
          }
        }
      }
    }

    return new Version(isVersionParsed, verStr);
  }

  public static class Version {

    final public boolean isVersionParsed;
    final public DefaultArtifactVersion version;

    public Version(boolean isVersionParsed, String version) {
      this.isVersionParsed = isVersionParsed;
      this.version = new DefaultArtifactVersion(version);
    }
  }
}
