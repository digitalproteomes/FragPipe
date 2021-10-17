package com.dmtavt.fragpipe.cmd;

import static com.dmtavt.fragpipe.cmd.ToolingUtils.BATMASS_IO_JAR;
import static com.dmtavt.fragpipe.cmd.ToolingUtils.SMILE_CORE_JAR;
import static com.dmtavt.fragpipe.cmd.ToolingUtils.SMILE_MATH_JAR;

import com.dmtavt.fragpipe.Fragpipe;
import com.dmtavt.fragpipe.FragpipeLocations;
import com.dmtavt.fragpipe.api.InputLcmsFile;
import java.awt.Component;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.lambda.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmdMoreRescore extends CmdBase {
  private static final Logger log = LoggerFactory.getLogger(CmdMoreRescore.class);
  public static String NAME = "MoreRescore";
  public static final String JAR_MORERESCORE_NAME = "morerescore-1.0.jar";
  public static final String JAR_MORERESCORE_MAIN_CLASS = "Features.MainClass";
  private static final String[] JAR_DEPS = {SMILE_CORE_JAR, SMILE_MATH_JAR, BATMASS_IO_JAR};
  private static final String DIANN_EXE = "diann/win/DiaNN.exe";
  private static final Pattern pattern1 = Pattern.compile("\\.pepXML$");
  private static final Pattern pattern2 = Pattern.compile("_rank[0-9]+\\.pepXML$");

  public CmdMoreRescore(boolean isRun, Path workDir) {
    super(isRun, workDir);
  }

  @Override
  public String getCmdName() {
    return NAME;
  }

  public boolean configure(Component comp, int ramGb, int threads, Map<InputLcmsFile, List<Path>> lcmsToFraggerPepxml, boolean predictRT, boolean predictSpectra) {
    initPreConfig();

    final List<Path> classpathJars = FragpipeLocations.checkToolsMissing(Seq.of(JAR_MORERESCORE_NAME).concat(JAR_DEPS));
    if (classpathJars == null) {
      return false;
    }

    final List<Path> diannPath = FragpipeLocations.checkToolsMissing(Seq.of(DIANN_EXE));
    if (diannPath == null || diannPath.isEmpty()) {
      System.err.println("Cannot find DIA-NN executable file.");
      return false;
    }
    if (diannPath.size() > 1) {
      System.err.print("There are more than one DIA-NN executable file: ");
      for (Path p : diannPath) {
        System.err.print(p.toAbsolutePath() + "; ");
      }
      System.err.println();
      return false;
    }

    final Path paramPath = wd.resolve("morerescore_params.txt");

    if (Files.exists(paramPath.getParent())) { // Dry run does not make directories, so does not write the file.
      try {
        BufferedWriter bufferedWriter = Files.newBufferedWriter(paramPath);
        bufferedWriter.write("fragger = null\n");
        bufferedWriter.write("useDetect = false\n");
        bufferedWriter.write("numThreads = " + threads + "\n");
        bufferedWriter.write("DiaNN = " + diannPath.get(0) + "\n");
        bufferedWriter.write("renamePin = 1\n");
        bufferedWriter.write("useRT = " + (predictRT ? "true" : "false") + "\n");
        bufferedWriter.write("useSpectra = " + (predictSpectra ? "true" : "false") + "\n");

        // compute unique lcms file directories
        bufferedWriter.write("mzmlDirectory = ");
        Set<Path> lcmsDirsUnique = Seq.seq(lcmsToFraggerPepxml.keySet()).map(lcms -> lcms.getPath().getParent()).toSet();
        for (Path path : lcmsDirsUnique) {
          bufferedWriter.write(path.toString() + " ");
        }
        bufferedWriter.write("\n");

        bufferedWriter.write("pinPepXMLDirectory = ");
        Set<String> pinFiles = new HashSet<>();
        for (Entry<InputLcmsFile, List<Path>> e : lcmsToFraggerPepxml.entrySet()) {
          for (Path pepxml : e.getValue()) {
            if (e.getKey().getDataType().contentEquals("DDA")) {
              Matcher matcher = pattern1.matcher(wd.relativize(pepxml).toString());
              pinFiles.add(matcher.replaceAll(".pin"));
            } else {
              Matcher matcher = pattern2.matcher(wd.relativize(pepxml).toString());
              pinFiles.add(matcher.replaceAll(".pin"));
            }
          }
        }
        bufferedWriter.write(String.join(" ", pinFiles) + "\n");
        bufferedWriter.close();
      } catch (IOException ex) {
        ex.printStackTrace();
        return false;
      }
    }

    List<String> cmd = new ArrayList<>();
    cmd.add(Fragpipe.getBinJava());
    if (ramGb > 0) {
      cmd.add("-Xmx" + ramGb + "G");
    }

    cmd.add("-cp");
    cmd.add(constructClasspathString(classpathJars));
    cmd.add(JAR_MORERESCORE_MAIN_CLASS);
    cmd.add("--paramsList");
    cmd.add(paramPath.toAbsolutePath().toString());

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(wd.toFile());
    pbis.add(PbiBuilder.from(pb));

    isConfigured = true;
    return true;
  }
}