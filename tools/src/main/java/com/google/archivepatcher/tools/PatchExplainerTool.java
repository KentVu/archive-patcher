// Copyright 2016 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.tools;

import com.google.archivepatcher.explainer.EntryExplanation;
import com.google.archivepatcher.explainer.PatchExplainer;
import com.google.archivepatcher.explainer.PatchExplanation;
import com.google.archivepatcher.generator.bsdiff.BsDiffDeltaGenerator;
import com.google.archivepatcher.shared.DeflateCompressor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple command-line tool for explaining patches.
 */
public class PatchExplainerTool extends AbstractTool {

  /**
   * Usage instructions for the command line.
   */
  private static final String USAGE =
      "java -cp <classpath> com.google.archivepatcher.tools.PatchExplainerTool <options>\n"
          + "\nOptions:\n"
          + "  --old           the old file\n"
          + "  --new           the new file\n"
          + "  --json          output JSON results instead of plain text\n"
          + "\nExamples:\n"
          + "  To explain a patch from OLD to NEW, dumping plain human-readable text output:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.PatchExplainerTool \\\n"
          + "      --old OLD --new NEW\n"
          + "  To explain a patch from OLD to NEW, dumping JSON-formatted output:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.PatchExplainerTool \\\n"
          + "      --old OLD --new NEW --json\n";

  /**
   * Runs the tool. See usage instructions for more information.
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   */
  public static void main(String... args) throws IOException {
    new PatchExplainerTool().run(args);
  }

  /**
   * Used for pretty-printing sizes and counts.
   */
  private final NumberFormat format = NumberFormat.getNumberInstance();

  /**
   * Runs the tool. See usage instructions for more information.
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   */
  public void run(String... args) throws IOException {
    String oldPath = null;
    String newPath = null;
    boolean outputJson = false;
    Iterator<String> argIterator = new LinkedList<String>(Arrays.asList(args)).iterator();
    while (argIterator.hasNext()) {
      String arg = argIterator.next();
      if ("--old".equals(arg)) {
        oldPath = popOrDie(argIterator, "--old");
      } else if ("--new".equals(arg)) {
        newPath = popOrDie(argIterator, "--new");
      } else if ("--json".equals(arg)) {
        outputJson = true;
      } else {
        exitWithUsage("unknown argument: " + arg);
      }
    }
    if (oldPath == null || newPath == null) {
      exitWithUsage("missing required argument(s)");
    }
    File oldFile = getRequiredFileOrDie(oldPath, "old file");
    File newFile = getRequiredFileOrDie(newPath, "new file");
    DeflateCompressor compressor = new DeflateCompressor();
    compressor.setCaching(true);
    compressor.setCompressionLevel(9);
    PatchExplainer explainer =
        new PatchExplainer(new DeflateCompressor(), new BsDiffDeltaGenerator());
    PatchExplanation patchExplanation =
        new PatchExplanation(explainer.explainPatch(oldFile, newFile));
    if (outputJson) {
      patchExplanation.writeJson(new PrintWriter(System.out));
    } else {
      dumpPlainText(patchExplanation);
    }
  }

  private void dumpPlainText(PatchExplanation patchExplanation) {
    dumpPlainText(patchExplanation.getExplainedAsNew());
    dumpPlainText(patchExplanation.getExplainedAsChanged());
    dumpPlainText(patchExplanation.getExplainedAsUnchangedOrFree());
    System.out.println("----------");
    System.out.println(
        "Num unchanged files: " + patchExplanation.getExplainedAsUnchangedOrFree().size());
    System.out.println(
        "Num changed files:   "
            + patchExplanation.getExplainedAsChanged().size()
            + " (estimated patch size "
            + format.format(patchExplanation.getEstimatedChangedSize())
            + " bytes)");
    System.out.println(
        "Num new files:       "
            + patchExplanation.getExplainedAsNew().size()
            + " (estimated patch size "
            + format.format(patchExplanation.getEstimatedNewSize())
            + " bytes)");
    long estimatedTotalSize =
        patchExplanation.getEstimatedChangedSize() + patchExplanation.getEstimatedNewSize();
    System.out.println(
        "Estimated total patch size: " + format.format(estimatedTotalSize) + " bytes");
  }

  private void dumpPlainText(List<EntryExplanation> explanations) {
    for (EntryExplanation entryExplanation : explanations) {
      String text = toPlainText(entryExplanation);
      if (text != null) {
        System.out.println(text);
      }
    }
  }

  /**
   * Returns the path from an {@link EntryExplanation} as a UTF-8 string.
   * @param explanation the {@link EntryExplanation} to extract the path from
   * @return as described
   */
  private static String path(EntryExplanation explanation) {
    try {
      return new String(explanation.getPath().getData(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("System doesn't support UTF-8", e);
    }
  }

  private static String toPlainText(EntryExplanation explanation) {
    String path = path(explanation);
    if (explanation.isNew()) {
      return "New file '"
          + path
          + "', approximate size of data in patch: "
          + explanation.getCompressedSizeInPatch()
          + " bytes";
    }
    if (explanation.getCompressedSizeInPatch() > 0) {
      return "Changed file '"
          + path
          + "', approximate size of data in patch: "
          + explanation.getCompressedSizeInPatch()
          + " bytes";
    } else {
      return "Unchanged or zero-delta-cost file '" + path + "'";
    }
  }

  @Override
  protected String getUsage() {
    return USAGE;
  }
}