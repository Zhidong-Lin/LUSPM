package java1;

import java.io.*;
import java.util.*;

public class LUSM_naive {
    private int[] flatItems;   // 所有事务的 item 扁平化
    private int[] flatUtils;   // 所有事务的 utility 扁平化
    private int[] txnStarts;   // 每个事务在扁平数组中的起始下标
    Map<Integer, Integer[]> mapTidToItems = new LinkedHashMap<>();
    Map<Integer, Integer[]> mapTidToUtilities = new LinkedHashMap<>();
    Map<Integer, BitSet> mapItemToBitSet = new LinkedHashMap<>();
    List<List<Integer>> maxSequenceList = new ArrayList<>();
    Set<List<Integer>> maxSequenceSet = new HashSet<>();
    Set<List<Integer>> hasProcessedSequenceList = new LinkedHashSet<>();
    Map<List<Integer>, Integer> hasProcessedSequenceListAndId = new LinkedHashMap<>();
    Map<List<Integer>, Integer> lowUtilityPattern = new LinkedHashMap<>();

    int count = 0;
    //    记录每行序列的起始项的位置
    Set<Integer> sequenceId = new TreeSet<>();
    int max_utility;
    BufferedWriter writer = null;
    //String exteFile="src/main/resources/extension.txt";
    long runtime;
    long runtime2;
    long runtime3;
    long runtime4;
    long runtime5;

    long startTime;
    int patternCount;
    long candidatesCount;

    int maxLength;

    public void runAlgorithm(String input, int max_utility, int maxLength, BufferedWriter writer) throws IOException {

        System.out.println("此处是低效用非连续序列的原始挖掘算法");
        this.writer=writer;
        this.max_utility = max_utility;
        this.maxLength = maxLength;
        startTime = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();
        buffer.append("max_utility:").append(max_utility).append("\n");
        buffer.append("maxLength:").append(maxLength).append("\n");
        buffer.append("LUSM_naive").append("\n");
        writer.write(buffer.toString());
        writer.newLine();
        System.out.println("输出最大效用:" + max_utility);
        System.out.println("startTime:" + startTime);
        MemoryLogger.getInstance().checkMemory();
        loadFile_sequence(input);
//        System.gc();
        MemoryLogger.getInstance().checkMemory();
        buildIndex();
//        System.gc();
        MemoryLogger.getInstance().checkMemory();
        System.out.println("输出count:" + sequenceId);
        runtime2 = System.currentTimeMillis() - startTime;
        createBitmap();
//        System.gc();
        MemoryLogger.getInstance().checkMemory();
        runtime3 = System.currentTimeMillis() - startTime;
        processLUSM_naiveAlgorithm();
//        System.gc();
        MemoryLogger.getInstance().checkMemory();
        runtime = System.currentTimeMillis() - startTime;
        printLowUtilitySequence();
//        System.gc();
        MemoryLogger.getInstance().checkMemory();
        showStates();
    }
    public void loadFile_sequence(String path) throws IOException {
        final int[] tidCount = {0};
        final int[] item1 = {0};
        final int[] indexOfItem = {0};
        Set<Integer> setOfItems = new TreeSet<>();
        Map<Integer, Integer[]> mapTidToItems = new LinkedHashMap<>();
        Map<Integer, Integer[]> mapTidToItems_delete = new LinkedHashMap<>();
        Map<Integer, Integer[]> mapTidToUtilities = new LinkedHashMap<>();
        sequenceId.add(0);
//        List<Integer> tidCountList = new ArrayList<>();
        List<Integer> itemList = new ArrayList<>();
        List<Integer> indexOfItemList = new ArrayList<>();
        try (BufferedReader myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))))) {
            myInput.lines()
                    .filter(line -> !line.isEmpty() && line.charAt(0) != '#' && line.charAt(0) != '%' && line.charAt(0) != '@')
                    .forEach(thisLine -> {
                        String[] partitions = thisLine.split("SUtility:"); // 按 "SUtility:" 分割数据
                        if (partitions.length < 2) return;  // 如果没有有效的分割部分则跳过
//                        int lowerUtility=Integer.MAX_VALUE;
                        indexOfItem[0] = 0;
                        boolean key = false;
                        String[] itemUtilityPairs = partitions[0].trim().split(" ");  // 按空格分割项效用对
                        List<Integer> itemsIntList = new ArrayList<>();
                        List<Integer> utilsIntList = new ArrayList<>();
//                        boolean containsMaxUtility = false;
                        for (String pair : itemUtilityPairs) {
                            if (pair.isEmpty()) continue;

                            // 解析 "item[utility]" 格式
                            String[] itemUtility = pair.split("\\[|\\]"); // 按 "[" 和 "]" 分割
                            if (itemUtility.length != 2) continue;

                            try {
                                int item = Integer.parseInt(itemUtility[0].trim());
                                int utility = Integer.parseInt(itemUtility[1].trim());
                                if(this.maxLength==Integer.MAX_VALUE){
                                }

                                indexOfItem[0]++;
                                setOfItems.add(item);
                                itemsIntList.add(item);
                                utilsIntList.add(utility);
//                                if(utility<lowerUtility){lowerUtility=utility;}

                            } catch (NumberFormatException e) {
                                // 处理解析错误的情况
                                System.out.println("解析项或效用错误: " + pair);
                            }
                        }
                        count += itemsIntList.size();
                        mapTidToItems.put(tidCount[0], itemsIntList.toArray(new Integer[0]));
                        maxSequenceList.add(itemsIntList);
                        mapTidToUtilities.put(tidCount[0], utilsIntList.toArray(new Integer[0]));
                        sequenceId.add(count);
                        tidCount[0]++;
//                        this.lowestOfSeq.put(itemsIntList,lowerUtility);
                    });
            this.mapTidToItems = mapTidToItems;
            this.mapTidToUtilities = mapTidToUtilities;
            printMapData("mapTidToItems", mapTidToItems);
            printMapData("mapTidToUtilities", mapTidToUtilities);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void printMapData(String mapName, Map<Integer, Integer[]> mapData) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Printing:"+mapName+":\n");
        System.out.println("Printing " + mapName + ":");
        for (Map.Entry<Integer, Integer[]> entry : mapData.entrySet()) {
            buffer.append("Line"+entry.getKey()+":");
            buffer.append(Arrays.toString(entry.getValue())+"\n");
            System.out.print("Line " + entry.getKey() + ": ");
            System.out.println(Arrays.toString(entry.getValue()));
        }
        writer.write(buffer.toString());
        writer.newLine();
    }
    public void buildIndex() {
        int total = 0;
        for (Integer[] u : mapTidToUtilities.values()) total += u.length;

        flatItems = new int[total];
        flatUtils = new int[total];
        txnStarts = new int[mapTidToUtilities.size() + 1]; // 多一位方便二分

        int off = 0, tid = 0;
        for (Map.Entry<Integer, Integer[]> e : mapTidToItems.entrySet()) {
            txnStarts[tid++] = off;
            Integer[] items = e.getValue();
            Integer[] utils = mapTidToUtilities.get(e.getKey());
            for (int i = 0; i < items.length; i++) {
                flatItems[off]   = items[i];
                flatUtils[off++] = utils[i];
            }
        }
        txnStarts[tid] = off; // 哨兵
    }


    public void createBitmap() throws IOException {
        int countBit = 0;
            for (Map.Entry<Integer, Integer[]> entry : mapTidToItems.entrySet()) {
                Integer[] items = entry.getValue();
                for (Integer key : items) {
                    mapItemToBitSet.computeIfAbsent(key, k -> new BitSet()).set(countBit);
                    countBit++;
                }
            }
        // 创建每一项的位图

    }
    public List<List<Integer>> getUtilities(List<Integer> seq) {
        final int k = seq.size();
        if (k == 0) return Collections.emptyList();

        /* 1. 预取 bitmap 及 long[] */
        BitSet[] bs = new BitSet[k];
        long[][] ws = new long[k][];
        int[] lens = new int[k];
        for (int i = 0; i < k; i++) {
            bs[i] = mapItemToBitSet.get(seq.get(i));
            if (bs[i] == null || bs[i].isEmpty()) return Collections.emptyList();
            ws[i] = bs[i].toLongArray();
            lens[i] = bs[i].length();
        }

        /* 2. 把 sequenceId 变成升序数组，用于区间划分 */
        int[] sid = sequenceId.stream().mapToInt(Integer::intValue).toArray();

        /* 3. 枚举起点 */
        List<List<Integer>> res = new ArrayList<>();
        long[] w0 = ws[0];
        for (int wi = 0; wi < w0.length; wi++) {
            for (long word = w0[wi]; word != 0; ) {
                int tz = Long.numberOfTrailingZeros(word);
                int idx0 = (wi << 6) + tz;
                word &= word - 1;               // 快速抹最低 1
                int up = upperBound(sid, idx0); // 事务右边界
                if (up == -1) continue;

                /* 4. 顺序匹配后续 k-1 项 */
                int[] pos = new int[k];
                pos[0] = idx0;
                boolean ok = true;
                for (int i = 1; i < k; i++) {
                    int nxt = nextSetBit(ws[i], idx0 + 1, up);
                    if (nxt == -1) { ok = false; break; }
                    pos[i] = nxt;
                    idx0 = nxt;
                }
                if (ok) {
                    List<Integer> line = new ArrayList<>(k);
                    for (int p : pos) line.add(getUtility(p));
                    res.add(line);
                }
            }
        }
        return res;
    }
    private int upperBound(int[] seqIds, int val) {
        for (int v : seqIds) {
            if (v > val) return v;
        }
        return -1;
    }
    private static int nextSetBit(long[] words, int from, int toExclusive) {
        if (from >= toExclusive) return -1;
        int wIdx = from >>> 6;
        if (wIdx >= words.length) return -1;
        long word = words[wIdx] & (~0L << (from & 63));
        while (true) {
            if (word != 0) {
                int tz = Long.numberOfTrailingZeros(word);
                int idx = (wIdx << 6) + tz;
                return idx < toExclusive ? idx : -1;
            }
            if (++wIdx >= words.length || (wIdx << 6) >= toExclusive) return -1;
            word = words[wIdx];
        }
    }
    public int countUtility(List<Integer> sequence) {
        if (sequence == null || sequence.isEmpty()) return -1;

        final int n = sequence.size();
        // 1. 把 sequenceId 变成有序数组，便于二分
        final int[] sid = sequenceId.stream().mapToInt(Integer::intValue).toArray();
        // 2. 预取所有 BitSet，避免 map 反复查找
        final BitSet[] bss = new BitSet[n];
        for (int i = 0; i < n; i++) {
            bss[i] = mapItemToBitSet.get(sequence.get(i));
            if (bss[i] == null) return 0;          // 只要有一个 item 不存在，直接返回 0
        }

        int total = 0;

        // 3. 遍历第一个 item 的所有出现位置
        for (int idx = bss[0].nextSetBit(0); idx >= 0; idx = bss[0].nextSetBit(idx + 1)) {

            // 3.1 用二分快速找到当前 idx 对应的 upindex
            int up = upperBound(sid, idx);
            if (up == -1) break;                   // 后面没有更大的 sequenceId 了

            int cur = idx;
            int util = getUtility(cur);            // 首个 item 的 utility
            boolean ok = true;

            // 3.2 匹配后续 item
            for (int i = 1; i < n; i++) {
                // 从 cur+1 开始找下一个 1
                int nxt = bss[i].nextSetBit(cur + 1);
                if (nxt < 0 || nxt >= up) {        // 没找到或在区间外
                    ok = false;
                    break;
                }
                util += getUtility(nxt);
                cur = nxt;
            }
            if (ok) total += util;
        }
        return total;
    }

    public int getUtility(int index) {
        if (index < 0 || index >= flatUtils.length) return 0;
        return flatUtils[index];
    }



    // 判断sequence是否是mainSequence的子序列（考虑顺序）
    private void processLowUtilitySequence(List<Integer> sequence, int utility) throws IOException {
//        if (!hasProcessedSequenceList.contains(sequence) && hasKnownHighUtilitySequence.contains(sequence) == false) {
        if (!hasProcessedSequenceList.contains(sequence)&&lowUtilityPattern.get(sequence)==null) {
            logLowUtilityInfo(sequence, utility);

        }
    }

    private void logLowUtilityInfo(List<Integer> sequence, int utility) throws IOException {
//        if (!hasProcessedSequenceList.contains(sequence) && hasKnownHighUtilitySequence.contains(sequence) == false) {
            System.out.println("获得低效用序列：" + sequence);
            StringBuilder buffer = new StringBuilder();
            buffer.append("获得低效用序列：" + sequence).append("\n");
            writer.write(buffer.toString());
            writer.newLine();
            hasProcessedSequenceList.add(sequence);
//            candidatesCount++;
            patternCount++;
            lowUtilityPattern.put(sequence, utility);
    }


    public void processLUSM_naiveAlgorithm() throws IOException {
        System.out.println("当前运行到LUSM");
        int size = maxSequenceList.size();
        int num_now = 0;
        maxSequenceList.sort((a, b) -> b.size() - a.size());
        for (List<Integer> sequence : maxSequenceList) {
            System.out.println(num_now + "/" + size);
            StringBuilder buffer = new StringBuilder();
            buffer.append(num_now + "/" + size);
            writer.write(buffer.toString());
            writer.newLine();
            num_now++;
            if(sequence.size()>0){
                List<List<Integer>> utilities = getUtilities(sequence);
                if(sequence.size()==0){
                    continue;
                }
                int utilityOfSequence = countUtility_FL(utilities);
                if (utilityOfSequence == -1) System.out.println("此序列为空！");
                candidatesCount++;
//            if (utilityOfSequence <= max_utility && hasProcessedSequenceList.contains(sequence) == false&&hasProcessedSequenceListAndId.get(sequence) == null && hasKnownHighUtilitySequence.contains(sequence) == false) {
                if (utilityOfSequence <= max_utility && hasProcessedSequenceList.contains(sequence) == false&&hasProcessedSequenceListAndId.get(sequence) == null) {
//                    processLowUtilitySequence_improved(sequence, utilityOfSequence, -1);
//                processLowUtilitySequence_improved(sequence,utilityOfSequence);
                    processLowUtilitySequence(sequence,utilityOfSequence);
                }
                skrinkage(sequence,0);
//                hadProcessedMaxSequence++;
            }

//                hasDeleteSequenceList.add(sequence);
////                cut2(sequence);
//                hasDeleteSequenceList.add(sequence);
//                cut(sequence);
//                LUSM(sequence);
        }
    }
    public void skrinkage( List<Integer> sequence,int itemNum) throws IOException {
//        if(isProcessed(sequence)){return;}
        System.out.println("正在处理序列："+sequence);
        candidatesCount++;
        if(itemNum+1<sequence.size()){
            List<Integer> sequenceNew=new ArrayList<>(sequence);
            skrinkage(sequenceNew,itemNum+1);
        }
        if(itemNum+1==sequence.size()){
            int utility=countUtility(sequence);
            if(utility<=max_utility){
                processLowUtilitySequence(sequence,utility);

            }
        }
        if(itemNum<sequence.size()){
            List<Integer> sequenceNew=new ArrayList<>(sequence);
            sequenceNew.remove(itemNum);
            if(sequenceNew.size()==0){
                return;
            }
//            System.out.println("正在处理序列："+sequenceNew);
//            List<List<Integer>> utilities=getUtilities(sequenceNew);
            int utility=countUtility(sequenceNew);
            if(utility<=max_utility){
//                processLowUtilitySequence_improved(sequenceNew,utility);
                processLowUtilitySequence(sequenceNew,utility);

            }
            if(itemNum<sequenceNew.size()){
                skrinkage(sequenceNew,itemNum);
            }
        }

    }

//    public int countUtility_FL(List<List<Integer>> utilities) {
//        return utilities.stream()
//                .filter(Objects::nonNull)
//                .flatMap(List::stream)
//                .reduce(0, Integer::sum);
//    }
public int countUtility_FL(List<List<Integer>> utilities, int num) {
    if (utilities == null || num <= 0) {
        return 0;
    }

    int sum = 0;
    for (List<Integer> list : utilities) {
        int bound = Math.min(num, list.size());
        for (int i = 0; i < bound; i++) {
            sum += list.get(i);
            // 提前剪枝（可选）
            if (sum > max_utility) {
                return sum;   // 或 return max_utility + 1; 视业务而定
            }
        }
    }
    return sum;
    }
    public int countUtility_FL(List<List<Integer>> utilities) {
        int sum = 0;
        for (List<Integer> list : utilities) {
            for (int v : list) sum += v;
        }
        return sum;
    }
//}
//    public int countUtility_FL(List<List<Integer>> utilities,int num) {
//        int utilityOfSequence = 0;
//        for (List<Integer> utility : utilities) {
//            for(int i=0;i<num;i++){
//                utilityOfSequence += utility.get(i);
//            }
//        }
//        return  utilityOfSequence;
//    }

    public boolean isContains(List<Integer> sequence) {
        // 优化第一个条件
        if (maxSequenceSet.contains(sequence)) {
            return true;
        }

        // 并行化检查 containsSublist
        return maxSequenceList.parallelStream().anyMatch(maxSequence -> containsSublist(maxSequence, sequence));
    }

    private boolean containsSublist(List<Integer> maxSequence, List<Integer> sequence) {
        int n = maxSequence.size();
        int m = sequence.size();
        if (m > n) return false;

        // 滑动窗口优化
        for (int i = 0; i <= n - m; i++) {
            boolean match = true;
            for (int j = 0; j < m; j++) {
                if (!maxSequence.get(i + j).equals(sequence.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    public void printStats(List<Double> runTimelist, List<Double> memorylist, List<Long> candidateslist, List<Integer> patternlist) {

        runTimelist.add((double) runtime / 1000);
        memorylist.add(MemoryLogger.getInstance().getMaxMemory());
        candidateslist.add(candidatesCount);
        patternlist.add(patternCount);

    }
public void printLowUtilitySequence() throws IOException {
    writer.write("低效用非连续序列有：" + patternCount + "个\n");
    System.out.println("低效用序列有:" + patternCount + "个");

    if (patternCount > 0) {
        writer.write("输出低效用序列：\n");
        System.out.println("输出低效用序列：");

        int limit = 1000000; // 限制输出最多 10000 条记录（可调整）
        int count = 0;

        for (Map.Entry<List<Integer>, Integer> entry : lowUtilityPattern.entrySet()) {
            if (count++ >= limit) break;
            String line = "序列：" + entry.getKey() + "  效用：" + entry.getValue() + "\n";
            writer.write(line);
            System.out.println(line.trim());
        }
    }

    writer.write("输出低效用序列的数量：" + lowUtilityPattern.size() + "\n");
    writer.newLine();
}


    public void showStates() throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("存储数据运行时间为：" + (double) runtime2 / 1000 + "s").append("\n");
        buffer.append("获得项的位图运行时间为：" + (double) runtime4 / 1000 + "s").append("\n");
        buffer.append("获得最长序列运行时间为：" + (double) runtime5 / 1000 + "s").append("\n");
        buffer.append("预处理运行时间为：" + (double) runtime3 / 1000 + "s").append("\n");
        buffer.append("运行时间为：" + (double) runtime / 1000 + "s").append("\n");
        buffer.append("运行内存为：" + MemoryLogger.getInstance().getMaxMemory() + "MB").append("\n");
        buffer.append("总的内存为：" + (double) Runtime.getRuntime().totalMemory() / 1024d / 1024d + "MB").append("\n");
        buffer.append("空闲内存为：" + (double) Runtime.getRuntime().freeMemory() / 1024d / 1024d + "MB").append("\n");
        buffer.append("候选集数目为_改进版：" + candidatesCount).append("\n");
        buffer.append("模式数目为：" + patternCount).append("\n");
        writer.write(buffer.toString());
        writer.newLine();
        System.out.println("存储数据运行时间为：" + (double) runtime2 / 1000 + "s");
        System.out.println("获得项的位图运行时间为：" + (double) runtime4 / 1000 + "s");
        System.out.println("获得最长序列运行时间为：" + (double) runtime5 / 1000 + "s");
        System.out.println("预处理运行时间为：" + (double) runtime3 / 1000 + "s");
        System.out.println("运行时间为：" + (double) runtime / 1000 + "s");
        System.out.println("运行内存为：" + MemoryLogger.getInstance().getMaxMemory() + "MB");
        System.out.println("总的内存为：" + (double) Runtime.getRuntime().totalMemory() / 1024d / 1024d + "MB");
        System.out.println("空闲内存为：" + (double) Runtime.getRuntime().freeMemory() / 1024d / 1024d + "MB");
        System.out.println("候选集数目为：" + candidatesCount);
        System.out.println("模式数目为：" + patternCount);
    }

}

