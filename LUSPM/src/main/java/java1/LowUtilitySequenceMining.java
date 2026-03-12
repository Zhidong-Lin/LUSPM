package java1;

import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.List;
import java.sql.SQLOutput;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LowUtilitySequenceMining {
    int countOfCountUtilities=0;
    Set<int[]> preSet = new LinkedHashSet<>();
    // 1. 在 loadFile 之后调用一次即可
    private int[] flatItems;   // 所有事务的 item 扁平化
    private int[] flatUtils;   // 所有事务的 utility 扁平化
    private int[] txnStarts;   // 每个事务在扁平数组中的起始下标
    /**
     * 轻量级不可变序列 key，避免 List<Integer> 的哈希计算和装箱开销
     */
    final class SeqKey {
        private final int[] data;
        private final int hash;
        SeqKey(List<Integer> list) {
            data = new int[list.size()];
            for (int i = 0; i < data.length; i++) {
                data[i] = list.get(i);
            }
            hash = Arrays.hashCode(data);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SeqKey)) return false;
            SeqKey other = (SeqKey) obj;
            return Arrays.equals(data, other.data);
        }

        /* 可选：用于调试 */
        @Override
        public String toString() {
            return Arrays.toString(data);
        }
    }
    Map<Integer, Integer[]> mapTidToItems = new LinkedHashMap<>();
    Map<Integer, Integer[]> mapTidToItems_deleted = new LinkedHashMap<>();
    Map<Integer, Integer[]> mapTidToUtilities = new LinkedHashMap<>();
    Map<Integer, BitSet> mapItemToBitSet = new LinkedHashMap<>();
    List<List<Integer>> maxSequenceList = new ArrayList<>();
    List<List<Integer>> maxSequenceList2 = new ArrayList<>();
    Set<List<Integer>> maxSequenceSet = new HashSet<>();
    Map<List<Integer>,Integer> lowestOfSeq =new LinkedHashMap<>();
    Set<List<Integer>> hasProcessedMaxSequenceSet = new LinkedHashSet<>();
    Set<List<Integer>> hasProcessedSequenceList = new LinkedHashSet<>();
    Map<List<Integer>, Integer> hasProcessedSequenceListAndId = new LinkedHashMap<>();
    Set<List<Integer>> hasDeleteSequenceList = new LinkedHashSet<>();
    Map<List<Integer>, Integer> lowUtilityPattern = new LinkedHashMap<>();
    int hadProcessedMaxSequence=0;

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
    public static List<Double> runTime = new ArrayList<>();
    public static List<Double> memory = new ArrayList<>();
    public static List<Long> candidates = new ArrayList<>();
    public static List<Integer> pattern = new ArrayList<>();
    long startTime;
    int patternCount;
    long candidatesCount;
//    private int[] prefixSum; // prefixSum[i] = sum(flatUtils[0..i-1])
    int maxLength;

    public void runAlgorithm(String input, int max_utility, int maxLength, BufferedWriter writer) throws IOException {
        MemoryLogger.getInstance().checkMemory();
        System.out.println("此处是低效用非连续序列挖掘算法");
        this.writer=writer;
        this.max_utility = max_utility;
        this.maxLength = maxLength;
        startTime = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();
        buffer.append("max_utility:").append(max_utility).append("\n");
        buffer.append("maxLength:").append(maxLength).append("\n");
        writer.write(buffer.toString());
        writer.newLine();
        System.out.println("输出最大效用:" + max_utility);
        System.out.println("startTime:" + startTime);
        loadFile_sequence(input);
        buildIndex();
        System.out.println("输出count:" + sequenceId);
        runtime2 = System.currentTimeMillis() - startTime;
        createBitmap();
        getMaxSequence(maxLength);
        runtime3 = System.currentTimeMillis() - startTime;
        processCoreAlgorithm();
//        processCoreAlgorithm_improved();
        runtime = System.currentTimeMillis() - startTime;
        printLowUtilitySequence();
        showStates();
        MemoryLogger.getInstance().checkMemory();
        printStats(runTime, memory, candidates, pattern);


    }
    public void loadFile_sequence(String path) throws IOException {
        final int[] tidCount = {0};
//        final int[] item1 = {0};
        final int[] indexOfItem = {0};
//        Set<Integer> setOfItems = new TreeSet<>();
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
                        List<Integer> itemsIntList_actual = new ArrayList<>();
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
                                /*
                                * 裁剪策略：存储效用小于最低效用的项；
                                *
                                * */
//                                if(this.maxLength==Integer.MAX_VALUE){
                                    if(utility<=this.max_utility){
                                        itemsIntList_actual.add(item);
                                    }
//                                }

                                indexOfItem[0]++;
//                                setOfItems.add(item);
                                itemsIntList.add(item);
                                utilsIntList.add(utility);
//                                if(utility<lowerUtility){lowerUtility=utility;}

                            } catch (NumberFormatException e) {
                                // 处理解析错误的情况
                                System.out.println("解析项或效用错误: " + pair);
                            }
                        }
                        count += itemsIntList.size();
                        mapTidToItems_delete.put(tidCount[0], itemsIntList_actual.toArray(new Integer[0]));
                        mapTidToItems.put(tidCount[0], itemsIntList.toArray(new Integer[0]));
                        mapTidToUtilities.put(tidCount[0], utilsIntList.toArray(new Integer[0]));
                        sequenceId.add(count);
                        tidCount[0]++;
//                        this.lowestOfSeq.put(itemsIntList,lowerUtility);
                    });
            this.mapTidToItems_deleted=mapTidToItems_delete;
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
public static List<List<Integer>> deduplicateUtilities(List<List<Integer>> utilities) {
    return new ArrayList<>(new LinkedHashSet<>(utilities));
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
    public void getMaxSequence(int maxLength) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("当前运行到 preprocess\n");
        System.out.println("当前运行到 preprocess");
        System.out.println("2222");
        runtime4 = System.currentTimeMillis() - startTime;

        // **优化序列处理**
        List<Integer> listItems = new ArrayList<>();  // 复用 List，减少创建对象
//        if(this.maxLength==Integer.MAX_VALUE){
            for (Map.Entry<Integer, Integer[]> entry : mapTidToItems_deleted.entrySet()) {
                Integer[] items = entry.getValue();
                int length = items.length;
                    int i=0;
                    listItems.clear();  // 复用 List
                    for (int j = i; j < length ; j++) {
                        if (items[j] == -1) {
                            break;
                        }
                        listItems.add(items[j]);
                    }
                    if (!listItems.isEmpty() && !isContains(listItems,maxSequenceList2)) {
                        addSequence(new ArrayList<>(listItems));  // 传递副本，确保数据一致
                    }

            }
        for(List<Integer> sequence:maxSequenceList2){
            System.out.println("正在处理序列:"+sequence);
            if(sequence.size()==0){
                continue;
            }
            List<List<Integer>> utilities=getUtilities(sequence);
            if(utilities.size()>1){
                processed(sequence,utilities);
            }
            else{
                if(!isContains(sequence,maxSequenceList)) maxSequenceList.add(sequence);
            }
//            processed(sequence,utilities);
        }
        System.out.println("333");
        buffer.append("输出最大序列");
        buffer.append(maxSequenceList);
        System.out.println("输出最大序列");
        System.out.println(maxSequenceList);
        writer.write(buffer.toString());
        writer.newLine();
        runtime5 = System.currentTimeMillis() - startTime;
    }
    public void addSequence(List<Integer> sequence) {
        maxSequenceList2.add(sequence);
    }

public void processed(List<Integer> sequence,
                      List<List<Integer>> utilities) throws IOException {

    for (int i = 0; i < sequence.size(); i++) {
        Set<Integer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int utilityOfItem = 0;

        for (List<Integer> utility : utilities) {
            Integer val = utility.get(i);   // 拿到对象引用
            if (seen.add(val)) {            // 同一个引用只统计一次
                utilityOfItem += val;
            }
        }

        if (utilityOfItem > this.max_utility) {
            sequence.remove(i);
            for (List<Integer> utility : utilities) {
                utility.remove(i);
            }
            i--;
        }
    }

    if (!isContains(sequence, maxSequenceList)) {
        maxSequenceList.add(sequence);
    }
}


    public int getLowestOfUtilities(List<List<Integer>> utilities) {
        return utilities.stream()
                .flatMapToInt(list -> list.stream().mapToInt(Integer::intValue))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

public List<List<Integer>> getUtilities(List<Integer> seq) {
    countOfCountUtilities++;
    final int k = seq.size();
    if (k == 0) return Collections.emptyList();

    /* 1. 预取 bitmap 及 long[] */
    BitSet[] bs = new BitSet[k];
    long[][] ws = new long[k][];
    for (int i = 0; i < k; i++) {
        bs[i] = mapItemToBitSet.get(seq.get(i));
        if (bs[i] == null || bs[i].isEmpty()) return Collections.emptyList();
        ws[i] = bs[i].toLongArray();
    }

    /* 2. 把 sequenceId 变成升序数组 */
    int[] sid = sequenceId.stream().mapToInt(Integer::intValue).toArray();

    /* 3. 枚举起点 */
    List<List<Integer>> res = new ArrayList<>();
    long[] w0 = ws[0];

    for (int wi = 0; wi < w0.length; wi++) {
        for (long word = w0[wi]; word != 0; ) {
            int tz = Long.numberOfTrailingZeros(word);
            int firstPos = (wi << 6) + tz;
            word &= word - 1;

            int up = upperBound(sid, firstPos);
            if (up == -1) continue;

            /* 4. 顺序匹配后续 k-1 项 */
            int[] pos = new int[k];
            pos[0] = firstPos;
            int cur = firstPos;
            boolean ok = true;

            // 判断是否需要做跨度剪枝
//            boolean hasMaxLengthLimit = (maxLength != Integer.MAX_VALUE);

            for (int i = 1; i < k; i++) {

                    // 无限制：正常匹配
                    int nxt = nextSetBit(ws[i], cur + 1, up);
                    if (nxt == -1) {
                        ok = false;
                        break;
                    }
                    pos[i] = nxt;
                    cur = nxt;
//                }
            }

            // ✅ 匹配成功后：检查最终跨度（仅当有限制时）

            // ✅ 收集效用（只要 ok 且通过跨度检查）
            if (ok) {
                List<Integer> line = new ArrayList<>(k);
                for (int p : pos) {
                    line.add(getUtility(p));
                }
                res.add(line);
            }
        }
    }
    return res;
}
    /* ---------- 4. 一次处理 long 的 nextSetBit ---------- */
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



//    /**
//     * 返回 seqIds 中第一个严格大于 val 的值，
//     * 若不存在返回 -1。
//     */
    private int upperBound(int[] seqIds, int val) {
        for (int v : seqIds) {
            if (v > val) return v;
        }
        return -1;
    }

public int countUtility(List<Integer> sequence) {
    if (sequence == null || sequence.isEmpty()) return -1;
    countOfCountUtilities++;
    final int n = sequence.size();

    // 1. 把 sequenceId 变成有序数组
    final int[] sid = sequenceId.stream().mapToInt(Integer::intValue).toArray();

    // 2. 预取 BitSet
    final BitSet[] bss = new BitSet[n];
    for (int i = 0; i < n; i++) {
        bss[i] = mapItemToBitSet.get(sequence.get(i));
        if (bss[i] == null) return 0; // 不存在则无匹配
    }

    int total = 0;
//    boolean hasLengthLimit = (maxLength != Integer.MAX_VALUE); // ✅ 缓存判断，避免重复比较

    // 3. 遍历第一个 item 的所有出现位置
    for (int firstPos = bss[0].nextSetBit(0); firstPos >= 0;
         firstPos = bss[0].nextSetBit(firstPos + 1)) {

        int up = upperBound(sid, firstPos);
        if (up == -1) break;

        int cur = firstPos;
        int util = getUtility(cur);
        boolean ok = true;

        // 3.2 匹配后续项（带跨度剪枝）
        for (int i = 1; i < n; i++) {

                // 无限制：正常匹配
                int nxt = bss[i].nextSetBit(cur + 1);
                if (nxt < 0 || nxt >= up) {
                    ok = false;
                    break;
                }
                util += getUtility(nxt);
                cur = nxt;
            }
//        }
        // ✅ 累加效用
        if (ok) {
            total += util;
        }
    }
    return total;
}
    /**

    /**
     * 把 mapTidToItems 与 mapTidToUtilities 转换成事务块列表，供后续 getUtilitiesFast 使用
     */

    /* 把 Integer[] 快速转 int[] */
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

public int getUtility(int index) {
    if (index < 0 || index >= flatUtils.length) return 0;
    return flatUtils[index];
}



    private void processLowUtilitySequence(List<Integer> sequence, int utility) throws IOException {
//        if (!hasProcessedSequenceList.contains(sequence) && hasKnownHighUtilitySequence.contains(sequence) == false) {
        if (!hasProcessedSequenceList.contains(sequence)&&lowUtilityPattern.get(sequence)==null) {
            logLowUtilityInfo(sequence, utility);

        }
    }

    private void logLowUtilityInfo(List<Integer> sequence, int utility) throws IOException {
        if(sequence.size()==0){return;}
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


    public void processCoreAlgorithm() throws IOException {
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
                if (utilityOfSequence <= max_utility && hasProcessedSequenceList.contains(sequence) == false&&hasProcessedSequenceListAndId.get(sequence) == null) {
                    if(sequence.size()<=maxLength){
                        List<Integer> sequenceNew=new ArrayList<>(sequence);
                        processLowUtilitySequence(sequenceNew,utilityOfSequence);
                    }
                    skrinkage(sequence,0);
                }
                else {
                    skrinkage_depth(sequence,utilities,0);
                }
            }

        }
    }

    public void skrinkage( List<Integer> sequence,int itemNum) throws IOException {
        candidatesCount++;

        if(itemNum+1<sequence.size()){
            List<Integer> sequenceNew=new ArrayList<>(sequence);
            skrinkage(sequenceNew,itemNum+1);
        }

        if(itemNum+1==sequence.size()&&sequence.size()<=maxLength){
            int utility=countUtility(sequence);
            if(utility<=max_utility){
                List<Integer> sequenceNew2=new ArrayList<>(sequence);
                processLowUtilitySequence(sequenceNew2,utility);

            }
        }
        if(itemNum<sequence.size()){
            List<Integer> sequenceNew=new ArrayList<>(sequence);
            sequenceNew.remove(itemNum);
            if(sequenceNew.size()==0){
                return;
            }
            List<List<Integer>> utilities=getUtilities(sequenceNew);
            int utility=countUtility_FL(utilities);
            if(utility<=max_utility){
                if(sequenceNew.size()<maxLength){
                    List<Integer> sequenceNew3=new ArrayList<>(sequenceNew);
                    processLowUtilitySequence(sequenceNew3,utility);
                }

                if(itemNum<sequenceNew.size()){
                    skrinkage(sequenceNew,itemNum);
                }
            }
            else{
                if(itemNum<sequenceNew.size()){
                    List<Integer> sequenceNew2=new ArrayList<>(sequenceNew);
                    skrinkage_depth(sequenceNew2,utilities,itemNum);
                }
            }
        }

    }
    public void skrinkage_depth( List<Integer> sequence,List<List<Integer>> utilities,int itemNum) throws IOException {
        candidatesCount++;
        pruneItem(sequence,utilities,itemNum);

        if(itemNum+1<sequence.size()){
            List<Integer> sequenceNew1=new ArrayList<>(sequence);
            List<List<Integer>> utilitiesNew1=copyUtilities(utilities);
            skrinkage_depth(sequenceNew1,utilitiesNew1,itemNum+1);
        }
        if(itemNum+1==sequence.size()){
            int utility=countUtility(sequence);
            if(utility<=max_utility&&sequence.size()<=maxLength){
                List<Integer> sequenceNew=new ArrayList<>(sequence);
                processLowUtilitySequence(sequenceNew,utility);

            }
        }
        if(itemNum<sequence.size()){
            sequence.remove(itemNum);
            if(sequence.size()==0){
                return;
            }
            List<List<Integer>> utilitiesOfFather=copyUtilities(utilities);
            for(List<Integer> utility:utilitiesOfFather){
                utility.remove(itemNum);
            }
            utilitiesOfFather=deduplicateUtilities(utilitiesOfFather);
                if (sequence.size()<=maxLength) {
                    if(countUtility_FL(utilitiesOfFather)<=max_utility){
                    List<List<Integer>> utilitiesNew = getUtilities(sequence);
                    if (countUtility(sequence) <= max_utility) {
//                    processLowUtilitySequence_improved(sequenceNew,countUtility(sequenceNew));
                        List<Integer> sequenceNew2 = new ArrayList<>(sequence);
                        processLowUtilitySequence(sequenceNew2, countUtility(sequenceNew2));
                        if (itemNum < sequence.size()) {
                            skrinkage(sequence, itemNum);
                        }
                    } else {
                        if (itemNum < sequence.size()) {
                            skrinkage_depth(sequence, utilitiesNew, itemNum);
                        }
                    }
                }
                else{
                    if (itemNum < sequence.size()) {
                        skrinkage_depth(sequence,utilitiesOfFather,itemNum);
                    }
                }
            }
            else{
                if(itemNum<sequence.size()){
                    skrinkage_depth(sequence,utilitiesOfFather,itemNum);
                }
            }
        }
    }


public void pruneItem(List<Integer> sequence,
                      List<List<Integer>> utilities,
                      int itemNum) throws IOException {

    if (itemNum < 0 || itemNum >= sequence.size()) {
        return;
    }

    List<Integer> deleteId = new ArrayList<>();
    int utilityOfFrontCertain = 0;

    /* 1. 计算已固定前缀的效用（itemNum 之前） */
    if (itemNum > 0) {
        utilityOfFrontCertain = countUtility_FL(utilities, itemNum);
    }

    /* 2. 向后扫描，决定哪些位置需要删除 */
    for (int i = itemNum; i + 1 < sequence.size(); i++) {
        // 按对象地址去重
        Set<Integer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int utilityOfFront = utilityOfFrontCertain;

        for (List<Integer> utility : utilities) {
            Integer val = utility.get(i);
            if (seen.add(val)) {          // 同一个对象只加一次
                utilityOfFront += val;
            }
        }

        if (utilityOfFront > max_utility) {
            deleteId.add(i);
        }
    }

    /* 3. 倒序删除，避免索引错位 */
    for (int i = deleteId.size() - 1; i >= 0; i--) {
        int idx = deleteId.get(i);
        sequence.remove(idx);
        for (List<Integer> utility : utilities) {
            utility.remove(idx);
        }
    }

    /* 4. 可选：进一步按地址去重整个 utilities（如果你需要） */
    utilities = deduplicateUtilities(utilities);
}

public List<List<Integer>> copyUtilities(List<List<Integer>> utilities) {
    List<List<Integer>> utilitiesNew = new ArrayList<>();
    for (List<Integer> utility : utilities) {
        utilitiesNew.add(new ArrayList<>(utility));
    }
    return utilitiesNew;
}
    public int countUtility_FL(List<List<Integer>> utilities) {
        int sum = 0;
        for (List<Integer> list : utilities) {
            for (int v : list) sum += v;
        }
        return sum;
    }

    public int countUtility_FL(List<List<Integer>> utilities, int num) {
        if (utilities == null || num <= 0) {
            return 0;
        }
        int sum = 0;
        for(int i=0;i<num;i++){
            Set<Integer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (List<Integer> utility : utilities) {
                Integer val = utility.get(i);
                if (seen.add(val)) {          // 同一个对象只加一次
                    sum += val;
                }
                // 提前剪枝（可选）
                if (sum > max_utility) {
                    return sum;   // 或 return max_utility + 1; 视业务而定
                }
            }
        }
        return sum;
    }
    // 扁平数组专用
//    public int countUtilityFlat(int startInclusive, int endExclusive) {
//        if (startInclusive < 0 || endExclusive > flatUtils.length || startInclusive >= endExclusive)
//            return 0;
//        return prefixSum[endExclusive] - prefixSum[startInclusive];
//    }

public boolean isContains(List<Integer> seq, List<List<Integer>> set) {
    // 1. 一次性把查询序列转 int[]
    final int[] pat = seq.stream().mapToInt(Integer::intValue).toArray();
    final int n = pat.length;

    for (List<Integer> cand : set) {
        if (cand.size() < n) continue;          // 长度剪枝
        if (isSub(pat, cand)) return true;      // 指针版子序列判断
    }
    return false;
}

    /* 指针版子序列判断：时间 O(|main|)，空间 O(1) */
    private static boolean isSub(int[] pat, List<Integer> main) {
        int i = 0, m = pat.length;
        for (int v : main) {
            if (i < m && v == pat[i]) i++;
        }
        return i == m;
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

public void printStats(List<Double> runTime, List<Double> memory, List<Long> candidates, List<Integer> pattern) {
    runTime.add((double) runtime / 1000); // 运行时间，单位为秒
    memory.add(MemoryLogger.getInstance().getMaxMemory()); // 最大内存使用量，单位为MB
    candidates.add(candidatesCount); // 候选集数量
    pattern.add(patternCount); // 模式数量
}




public void printLowUtilitySequence() throws IOException {
    writer.write("低效用非连续序列有：" + patternCount + "个\n");
    System.out.println("低效用序列有:" + patternCount + "个");

    if (patternCount > 0) {
        writer.write("输出低效用序列：\n");
        System.out.println("输出低效用序列：");

        int limit = 1000000000; // 限制输出最多 1000000000 条记录（可调整）
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
        buffer.append("计算效用运行次数为：" + countOfCountUtilities).append("\n");
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
        System.out.println("计算效用运行次数为：" + countOfCountUtilities);
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

