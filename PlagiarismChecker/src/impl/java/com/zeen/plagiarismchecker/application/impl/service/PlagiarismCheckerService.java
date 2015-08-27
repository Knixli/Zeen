package com.zeen.plagiarismchecker.application.impl.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.zeen.plagiarismchecker.Article;
import com.zeen.plagiarismchecker.ArticleRepository;
import com.zeen.plagiarismchecker.Paragraph;
import com.zeen.plagiarismchecker.ParagraphEntry;
import com.zeen.plagiarismchecker.application.impl.FingerprintRepositoryInfo;
import com.zeen.plagiarismchecker.application.impl.PlagiarismChecker;
import com.zeen.plagiarismchecker.impl.ArticleRepositoryImpl;
import com.zeen.plagiarismchecker.impl.ContentAnalyzerType;
import com.zeen.plagiarismchecker.impl.PDFTextParagraphExtractor;

@javax.ws.rs.Path("/")
public class PlagiarismCheckerService {

    private static final Logger LOGGER = Logger
            .getLogger(PlagiarismCheckerService.class.getName());

    @POST
    @javax.ws.rs.Path("checkDocument")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ParagraphCheckResult> checkDocument(
            @QueryParam("document") String documentContent) {
        checkNotNull(documentContent, "documentContent");
        LOGGER.info(String.format("documentContent=%s", documentContent));
        List<String> paragraphs = this.getParagraphs(documentContent);
        List<ParagraphCheckResult> paragraphCheckResults = Lists
                .newArrayListWithCapacity(paragraphs.size());
        for (int i = 0; i < paragraphs.size(); ++i) {
            paragraphCheckResults.add(null);
        }
        IntStream
                .range(0, paragraphs.size())
                .parallel()
                .forEach(
                        i -> {
                            String paragraphContent = paragraphs.get(i);
                            List<CheckResult> checkResults = this
                                    .checkParagraph(paragraphContent);
                            ParagraphCheckResult paragraphCheckResult = new ParagraphCheckResult();
                            paragraphCheckResult
                                    .setParagraphContentToCheck(paragraphContent);
                            paragraphCheckResult.setCheckResults(checkResults);
                            paragraphCheckResults.set(i, paragraphCheckResult);
                        });
        String ret = String.format("Check done: results=%s", paragraphCheckResults);
        LOGGER.info(ret.length());
        return paragraphCheckResults;
    }

    private List<String> getParagraphs(String docContent) {
        if (Context.LOWERCASE) {
            List<String> paragraphs = Lists.newArrayList();
            PDFTextParagraphExtractor.extract(docContent).forEach(
                    paragraph -> {
                        paragraphs.add(paragraph.toLowerCase());
                    });
            return paragraphs;
        } else {
            return Lists.newArrayList(PDFTextParagraphExtractor
                    .extract(docContent));
        }
    }

    @GET
    @javax.ws.rs.Path("check")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CheckResult> check(
            @QueryParam("paragraph") String paragraphContent) {
        checkNotNull(paragraphContent, "paragraphContent");
        LOGGER.info(String.format("Checking:paragraphContent=%s",
                paragraphContent));
        List<CheckResult> checkResults = this.checkParagraph(paragraphContent);
        LOGGER.info(String.format("Check done: results=%s", checkResults));
        return checkResults;
    }

    private List<CheckResult> checkParagraph(String paragraphContent) {
        List<Iterable<Entry<ContentAnalyzerType, Iterable<ParagraphEntry>>>> checkResultsList = Lists
                .newArrayList();
        for (int i = 0; i < Context.CHECKERS.size(); ++i) {
            checkResultsList.add(null);
        }
        IntStream
                .range(0, Context.CHECKERS.size())
                .parallel()
                .forEach(
                        i -> {
                            checkResultsList.set(i, Context.CHECKERS.get(i)
                                    .check(paragraphContent));
                        });
        List<Entry<ContentAnalyzerType, Iterable<ParagraphEntry>>> checkResults = Lists
                .newArrayList();
        checkResultsList.forEach(item -> {
            item.forEach(entry -> {
                checkResults.add(entry);
            });
        });

        Map<ParagraphEntry, Set<ContentAnalyzerType>> paragraphToContentAnalizersMap = Maps
                .newHashMap();

        Map<Integer, Set<ParagraphEntry>> articleToParagraphsMap = Maps
                .newHashMap();
        checkResults
                .forEach(pair -> {
                    pair.getValue()
                            .forEach(
                                    paragraphEntry -> {
                                        Integer articleId = paragraphEntry
                                                .getArticleId();
                                        Set<ParagraphEntry> paragraphEntryList = articleToParagraphsMap
                                                .get(articleId);
                                        if (paragraphEntryList == null) {
                                            paragraphEntryList = Sets
                                                    .newLinkedHashSet();
                                            articleToParagraphsMap.put(
                                                    articleId,
                                                    paragraphEntryList);
                                        }
                                        paragraphEntryList.add(paragraphEntry);
                                        Set<ContentAnalyzerType> contentAnalizers = paragraphToContentAnalizersMap
                                                .get(paragraphEntry);
                                        if (contentAnalizers == null) {
                                            contentAnalizers = Sets
                                                    .newLinkedHashSet();
                                            paragraphToContentAnalizersMap.put(
                                                    paragraphEntry,
                                                    contentAnalizers);
                                        }
                                        contentAnalizers.add(pair.getKey());
                                    });
                });

        List<CheckResult> results = Lists.newArrayList();

        articleToParagraphsMap
                .entrySet()
                .forEach(
                        item -> {
                            Article article = Context.ARTICLE_REPOSITORY
                                    .getArticle(item.getKey());
                            assert article != null;
                            List<Paragraph> paragraphs = Lists
                                    .newArrayList(article.getParagraphs());
                            assert paragraphs != null;
                            item.getValue()
                                    .forEach(
                                            paragraphEntry -> {
                                                Paragraph paragraph = paragraphs.get(paragraphEntry
                                                        .getParagraphId());
                                                assert paragraph != null;
                                                results.add(new CheckResult(
                                                        article.getId(),
                                                        paragraph.getId(),
                                                        paragraph.getContent(),
                                                        Lists.newArrayList(paragraphToContentAnalizersMap
                                                                .get(paragraphEntry))));
                                            });
                        });
        return results;
    }

    static void setupContext(String[] args) throws ParseException, IOException {
        // refer to https://commons.apache.org/proper/commons-cli/usage.html to
        // build CLI
        // -r --articleRepositoryFolders path1,path2,...pathn
        // -a --contentAnalizers name1,name2,...namen
        // -i --indexPath path
        // -c --caseSensitive case sensitive match

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption(
                Option.builder("r")
                        .argName("paths")
                        .hasArg()
                        .required()
                        .longOpt("articleRepositoryFolders")
                        .desc("paths of article repository, separated by comma")
                        .build())
                .addOption(
                        Option.builder("a")
                                .argName("names")
                                .hasArg()
                                .required()
                                .longOpt("contentAnalyzers")
                                .desc("content analyzer names, separated by comma")
                                .build())
                .addOption(
                        Option.builder("c").argName("boolean")
                                .longOpt("caseSensitive")
                                .desc("case sensitive match").build())
                .addOption(
                        Option.builder("i")
                                .argName("paths")
                                .hasArg()
                                .required()
                                .longOpt("indexPaths")
                                .desc("index paths, separated by comma. Each index path corresponds to one checker; each content analizer will have an index under a path;")
                                .build());

        CommandLine line = parser.parse(options, args);
        List<String> articleRepositoryFolders = Lists.newArrayList(Splitter.on(
                ',').split(line.getOptionValue("articleRepositoryFolders")));
        List<Path> folders = Lists
                .newArrayListWithCapacity(articleRepositoryFolders.size());
        articleRepositoryFolders.forEach(folder -> {
            File file = new File(folder);
            checkArgument(file.exists() && file.isDirectory(),
                    "repository folder");
            folders.add(Paths.get(folder));
        });

        List<String> contentAnalizerNames = Lists.newArrayList(Splitter.on(',')
                .split(line.getOptionValue("contentAnalyzers")));
        List<String> indexPaths = Lists.newArrayList(Splitter.on(',').split(
                line.getOptionValue("indexPaths")));
        List<List<FingerprintRepositoryInfo>> fingerprintRepositoryInfoLists = Lists
                .newArrayListWithCapacity(indexPaths.size());

        indexPaths
                .forEach(item -> {
                    Path indexPath = Paths.get(item);
                    checkArgument(indexPath.toFile().exists()
                            && indexPath.toFile().isDirectory(), "indexPath");

                    List<FingerprintRepositoryInfo> fingerprintRepositoryInfoList = Lists
                            .newArrayListWithCapacity(contentAnalizerNames
                                    .size());

                    contentAnalizerNames.forEach(name -> {
                        File indexFile = indexPath.resolve(name).toFile();
                        checkArgument(
                                indexFile.exists() && !indexFile.isDirectory(),
                                "indexFile");
                        fingerprintRepositoryInfoList
                                .add(new FingerprintRepositoryInfo(
                                        ContentAnalyzerType.valueOf(name),
                                        indexFile));

                    });
                    fingerprintRepositoryInfoLists
                            .add(fingerprintRepositoryInfoList);
                });

        List<PlagiarismChecker> checkers = Lists
                .newArrayListWithCapacity(indexPaths.size());
        for (List<FingerprintRepositoryInfo> fingerprintRepositoryInfoList : fingerprintRepositoryInfoLists) {
            checkers.add(new PlagiarismChecker(fingerprintRepositoryInfoList));
        }
        Context.ARTICLE_REPOSITORY = new ArticleRepositoryImpl(folders);
        Context.CHECKERS = checkers;
        Context.LOWERCASE = !line.hasOption("caseSensitive");
    }

    static class Context {
        // support multiple checkers, each checker has its own index root, to
        // support incremental index
        static List<PlagiarismChecker> CHECKERS;
        static ArticleRepository ARTICLE_REPOSITORY;
        static boolean LOWERCASE;
    }

}