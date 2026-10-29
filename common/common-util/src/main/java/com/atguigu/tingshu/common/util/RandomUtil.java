package com.atguigu.tingshu.common.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 获取随机数
 * 
 * @author qianyi
 *
 */
public class RandomUtil {

	private static final Random random = new Random();

	private static final DecimalFormat fourdf = new DecimalFormat("0000");

	private static final DecimalFormat sixdf = new DecimalFormat("000000");

	public static String getFourBitRandom() {
		return fourdf.format(random.nextInt(10000));
	}

	public static String getSixBitRandom() {
		return sixdf.format(random.nextInt(1000000));
	}

	/**
	 * 给定数组，抽取n个数据（不重复、均匀随机）
	 *
	 * @param swQidsList
	 * @param n
	 * @return
	 */
	public static ArrayList getRandom(List list, int n) {

		ArrayList r = new ArrayList();

		if (list == null || list.isEmpty() || n <= 0) {
			return r;
		}

		//【修复】原实现用 HashMap 以随机数为 key 去重，键值 1~100 撞键后 values()
		// 元素个数会少于 n，取值时 robjs[i] 数组越界；改用洗牌保证不重复且均匀
		List<Integer> indexList = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			indexList.add(i);
		}
		Collections.shuffle(indexList, random);

		// n 超过集合大小时最多返回全部元素
		int count = Math.min(n, list.size());
		for (int i = 0; i < count; i++) {
			r.add(list.get(indexList.get(i)));
		}
		return r;
	}
}
