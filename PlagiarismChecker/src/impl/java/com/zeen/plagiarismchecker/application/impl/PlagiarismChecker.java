package com.zeen.plagiarismchecker.application.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.zeen.plagiarismchecker.FingerprintRepository;
import com.zeen.plagiarismchecker.ParagraphEntry;
import com.zeen.plagiarismchecker.impl.ContentAnalyzerType;
import com.zeen.plagiarismchecker.impl.FingerprintRepositoryBuilderImpl;
import com.zeen.plagiarismchecker.impl.FingerprintRepositoryImpl;

public class PlagiarismChecker {
    final List<FingerprintRepository> fingerprintRepositories;
    final List<FingerprintRepositoryInfo> fingerprintRepositoryInfoList;

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this.getClass())
                .add("fingerprintRepositoryInfoList",
                        this.fingerprintRepositoryInfoList).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fingerprintRepositoryInfoList);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PlagiarismChecker other = (PlagiarismChecker) obj;
        return Objects.equal(this.fingerprintRepositoryInfoList,
                other.fingerprintRepositoryInfoList);
    }

    public PlagiarismChecker(
            List<FingerprintRepositoryInfo> fingerprintRepositoryInfoList)
            throws IOException {
        checkNotNull(fingerprintRepositoryInfoList,
                "fingerprintRepositoryInfoList");

        this.fingerprintRepositoryInfoList = Lists
                .newArrayList(fingerprintRepositoryInfoList);
        this.fingerprintRepositories = Lists
                .newArrayListWithCapacity(fingerprintRepositoryInfoList.size());
        this.loadIndexes();
    }

    private void loadIndexes() throws IOException {
        for (int i = 0; i < this.fingerprintRepositoryInfoList.size(); ++i) {
            this.fingerprintRepositories.add(FingerprintRepositoryImpl
                    .load(this.fingerprintRepositoryInfoList.get(i)
                            .getIndexFile()));
        }
    }

    public Iterable<Entry<ContentAnalyzerType, Iterable<ParagraphEntry>>> check(
            String paragraph) {
        checkNotNull(paragraph, "paragraph");

        List<Entry<ContentAnalyzerType, Iterable<ParagraphEntry>>> results = Lists
                .newArrayListWithCapacity(this.fingerprintRepositories.size());
        for (int i = 0; i < this.fingerprintRepositories.size(); ++i) {
            results.add(null);
        }
        // check multiple indexes in parallel
        IntStream
                .range(0, this.fingerprintRepositoryInfoList.size())
                .parallel()
                .forEach(
                        i -> {
                            List<Iterable<CharSequence>> checkPointsList = Lists
                                    .newArrayList(this.fingerprintRepositoryInfoList
                                            .get(i).getContentAnalyzerType()
                                            .getContentAnalyzer()
                                            .analyze(paragraph));

                            long[] fingerprintBuffer = new long[checkPointsList
                                    .size()];
                            FingerprintRepositoryBuilderImpl.FINGERPRINT_BUILDER
                                    .buildFingerprints(checkPointsList,
                                            new StringBuilder(),
                                            fingerprintBuffer);
                            List<ParagraphEntry> paragraphEntries = Lists
                                    .newArrayList();
                            for (int j = 0; j < fingerprintBuffer.length; ++j) {
                                this.fingerprintRepositories
                                        .get(i)
                                        .getFingerprintEntries(
                                                FingerprintRepositoryImpl
                                                        .newFingerprint(fingerprintBuffer[j]))
                                        .forEach(
                                                paragraphEntry -> {
                                                    paragraphEntries
                                                            .add(paragraphEntry);
                                                });
                            }
                            results.set(i, new AbstractMap.SimpleEntry<>(
                                    this.fingerprintRepositoryInfoList.get(i)
                                            .getContentAnalyzerType(),
                                    paragraphEntries));
                        });
        return results;
    }

    static PlagiarismChecker getPlagiarismCheckerWithArgs(String[] args)
            throws ParseException, IOException {
        // build CLI
        // -a --contentAnalizers name1,name2,...namen
        // -i --indexPath path

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption(
                Option.builder("a").argName("names").hasArg().required()
                        .longOpt("contentAnalyzers")
                        .desc("content analyzer names, separated by comma")
                        .build())
                .addOption(
                        Option.builder("i")
                                .argName("path")
                                .hasArg()
                                .required()
                                .longOpt("indexPath")
                                .desc("index path, each content analizer will create an index under this path")
                                .build());

        CommandLine line = parser.parse(options, args);

        List<String> contentAnalizerNames = Lists.newArrayList(Splitter.on(',')
                .split(line.getOptionValue("contentAnalyzers")));
        Path indexPath = Paths.get(line.getOptionValue("indexPath"));
        checkArgument(indexPath.toFile().exists()
                && indexPath.toFile().isDirectory(), "indexPath");

        List<FingerprintRepositoryInfo> fingerprintRepositoryInfoList = Lists
                .newArrayListWithCapacity(contentAnalizerNames.size());

        contentAnalizerNames.forEach(name -> {
            // index file must exist
                File indexFile = indexPath.resolve(name).toFile();
                checkArgument(indexFile.exists() && !indexFile.isDirectory(),
                        "indexFile");
                fingerprintRepositoryInfoList
                        .add(new FingerprintRepositoryInfo(ContentAnalyzerType
                                .valueOf(name), indexFile));
            });

        if (!indexPath.toFile().exists()) {
            indexPath.toFile().mkdirs();
        }

        return new PlagiarismChecker(fingerprintRepositoryInfoList);
    }

    public static void main(final String[] args) throws ParseException,
            IOException {
        PlagiarismChecker plagiarismChecker = getPlagiarismCheckerWithArgs(args);
        try (Scanner scan = new Scanner(System.in)) {
            while (scan.hasNextLine()) {
                String line = scan.nextLine();
                if (line.equals("")) {
                    break;
                }
                System.out.println(plagiarismChecker.check(line));
            }
        }
    }
}
