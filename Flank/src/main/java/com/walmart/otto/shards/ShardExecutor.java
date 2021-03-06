package com.walmart.otto.shards;

import com.walmart.otto.configurator.Configurator;
import com.walmart.otto.tools.GcloudTool;
import com.walmart.otto.tools.GsutilTool;
import com.walmart.otto.tools.ToolManager;
import com.walmart.otto.utils.FilterUtils;
import com.walmart.otto.utils.XMLUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ShardExecutor {
  ExecutorService executorService;
  ShardCreator shardCreator;
  private Configurator configurator;
  private ToolManager toolManager;

  public ShardExecutor(Configurator configurator, ToolManager toolManager) {
    this.configurator = configurator;
    this.toolManager = toolManager;
    shardCreator = new ShardCreator(configurator);
  }

  public void execute(List<String> testCases, String bucket)
      throws InterruptedException, ExecutionException, IOException {

    executeShards(testCases, bucket);

    if (configurator.isFetchXMLFiles()) {
      fetchResults(toolManager.get(GsutilTool.class));
    }
  }

  public void executeShards(List<String> testCases, String bucket)
      throws InterruptedException, ExecutionException {
    List<Future> futures = new ArrayList<>();
    List<String> shards = shardCreator.getConfigurableShards(testCases);

    if (shards.isEmpty()) {
      shards = shardCreator.getShards(testCases);
    }

    executorService = Executors.newFixedThreadPool(shards.size());

    int shardIndex = configurator.getShardIndex();
    if (shardIndex != -1) {
      printTests(shards.get(shardIndex), shardIndex);
      futures.add(executeShard(shards.get(shardIndex), bucket, shardIndex));
    } else {
      System.out.println(
          shards.size() + " shards will be executed on: " + configurator.getDeviceIds() + "\n");

      for (int i = 0; i < shards.size(); i++) {
        printTests(shards.get(i), i);
        futures.add(executeShard(shards.get(i), bucket, i));
      }
    }

    executorService.shutdown();

    for (Future future : futures) {
      future.get();
    }
  }

  private Future executeShard(String testCase, String bucket, int shardIndex)
      throws RuntimeException {
    Callable<Void> testCaseCallable = getCallable(testCase, bucket, shardIndex);

    return executorService.submit(testCaseCallable);
  }

  private void fetchResults(GsutilTool gsutilTool) throws IOException, InterruptedException {
    XMLUtils.updateXMLFilesWithDeviceName(gsutilTool.fetchResults());
  }

  private void printTests(String testsString, int index) {
    String tests = FilterUtils.filterString(testsString, "class");
    if (tests.length() > 0 && tests.charAt(tests.length() - 1) == ',') {
      tests = tests.substring(0, tests.length() - 1);
    }
    System.out.println("Executing shard " + index + ": " + tests + "\n");
  }

  private Callable<Void> getCallable(String testCase, String bucket, int sharedIndex) {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        final GcloudTool gcloudTool = toolManager.get(GcloudTool.class);
        gcloudTool.runGcloud(testCase, bucket, sharedIndex);
        return null;
      }
    };
  }
}
