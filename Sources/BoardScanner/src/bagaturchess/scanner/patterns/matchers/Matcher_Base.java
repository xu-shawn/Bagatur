/**
 *  BagaturChess (UCI chess engine and tools)
 *  Copyright (C) 2005 Krasimir I. Topchiyski (k_topchiyski@yahoo.com)
 *  
 *  This file is part of BagaturChess program.
 * 
 *  BagaturChess is open software: you can redistribute it and/or modify
 *  it under the terms of the Eclipse Public License version 1.0 as published by
 *  the Eclipse Foundation.
 *
 *  BagaturChess is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Eclipse Public License for more details.
 *
 *  You should have received a copy of the Eclipse Public License version 1.0
 *  along with BagaturChess. If not, see http://www.eclipse.org/legal/epl-v10.html
 *
 */
package bagaturchess.scanner.patterns.matchers;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import bagaturchess.bitboard.impl.Constants;
import bagaturchess.bitboard.impl.utils.VarStatistic;
import bagaturchess.scanner.cnn.impl.ImageProperties;
import bagaturchess.scanner.cnn.impl.utils.ScannerUtils;
import bagaturchess.scanner.common.MatrixUtils;
import bagaturchess.scanner.common.ResultPair;


public abstract class Matcher_Base {
	
	
	private static final float SIZE_DELTA_PERCENT = 0.5f;
	
	
	protected ImageProperties imageProperties;
	
	
	protected Matcher_Base(ImageProperties _imageProperties) throws IOException {
		imageProperties = _imageProperties;
	}
	
	
	public ResultPair<String, MatchingStatistics> scan(int[][] grayBoard) {
		
		MatchingStatistics result = new MatchingStatistics();
		result.matcherName = this.toString();
				
		//VarStatistic boardStat = calculateStats(grayBoard);
		
		VarStatistic deviations = new VarStatistic(false);
		
		Map<Integer, VarStatistic> squaresStats = new HashMap<Integer, VarStatistic>();
		for (int i = 0; i < grayBoard.length; i += grayBoard.length / 8) {
			for (int j = 0; j < grayBoard.length; j += grayBoard.length / 8) {
				
				int file = i / (grayBoard.length / 8);
				int rank = j / (grayBoard.length / 8);
				int fieldID = 63 - (file + 8 * rank);
				
				int[][] squareMatrix = MatrixUtils.getSquarePixelsMatrix(grayBoard, i, j);
				
				VarStatistic squareStat = calculateStats(squareMatrix);
				squaresStats.put(fieldID, squareStat);
				
				deviations.addValue(squareStat.getDisperse(), squareStat.getDisperse());
			}
		}
		
		
		int[] pids = new int[64];
		for (int i = 0; i < grayBoard.length; i += grayBoard.length / 8) {
			for (int j = 0; j < grayBoard.length; j += grayBoard.length / 8) {
				
				int file = i / (grayBoard.length / 8);
				int rank = j / (grayBoard.length / 8);
				int fieldID = 63 - (file + 8 * rank);
				
				int[][] squareMatrix = MatrixUtils.getSquarePixelsMatrix(grayBoard, i, j);
				
				VarStatistic squareStat = squaresStats.get(fieldID);
				//System.out.println("squareDisperse=" + squareDisperse + ", squareEntropy=" + squareEntropy);
				
				int pid = -1;
				if (squareStat.getDisperse() < deviations.getEntropy() - deviations.getDisperse() / 7.9f) {
					pid = Constants.PID_NONE;
				} else {
					ResultPair<Integer, MatrixUtils.PatternMatchingData> pidAndData = getPID(squareMatrix, i, j, fieldID);
					pid = pidAndData.getFirst();
					MatrixUtils.PatternMatchingData data = pidAndData.getSecond();
					result.totalDelta += data.delta;
				}
				pids[fieldID] = pid;
				
				//System.out.println(squareDisperse);
			}
		}
		
		return new ResultPair<String, MatchingStatistics>(
				ScannerUtils.createFENFromPIDs(pids),
				result);
	}
	
	
	private ResultPair<Integer, MatrixUtils.PatternMatchingData> getPID(int[][] graySquareMatrix, int i1, int j1, int filedID) {
		
		int bgcolor = ScannerUtils.getAVG(graySquareMatrix);
		
		MatrixUtils.PatternMatchingData bestData = null;
		int bestPID = -1;
		int[][] bestPattern = null;
		
		for (int pid = Constants.PID_NONE; pid <= Constants.PID_B_KING; pid++) {
			
			int maxSize = graySquareMatrix.length;
			int startSize = (int) ((1 - SIZE_DELTA_PERCENT) * maxSize);
			
			for (int size = startSize; size <= maxSize; size++) {
				
				int[][] grayPattern = pid == Constants.PID_NONE ?
						ScannerUtils.createSquareImage(imageProperties, bgcolor, size)
						: ScannerUtils.createPieceImage(imageProperties, pid, bgcolor, size);
					
				MatrixUtils.PatternMatchingData curData = MatrixUtils.matchImages(graySquareMatrix, grayPattern);
				
				if (bestData == null || bestData.delta > curData.delta) {
					bestData = curData;
					bestPID = pid;
					bestPattern = grayPattern;
				}
			}
		}
		
		
		if (this instanceof ChessCom) {
			MatrixUtils.PatternMatchingData bestPatternData = new MatrixUtils.PatternMatchingData();
			bestPatternData.x = 0;
			bestPatternData.y = 0;
			bestPatternData.size = bestPattern.length;
			printInfo(bestPattern, bestPatternData, "" + filedID + "_bestPattern");
			
			printInfo(graySquareMatrix, bestData, "" + filedID);
		}
		
		return new ResultPair<Integer, MatrixUtils.PatternMatchingData>(bestPID, bestData);
	}
	
	
	private static VarStatistic calculateStats(int[][] grayMatrix) {
		
		VarStatistic stat = new VarStatistic(false);
		
		for (int i = 0; i < grayMatrix.length; i++) {
			for (int j = 0; j < grayMatrix.length; j++) {
				int cur = grayMatrix[i][j];
				stat.addValue(cur, cur);
			}
		}
		
		return stat;
	}
	
	
	protected static void printInfo(int[][] board, MatrixUtils.PatternMatchingData matcherData, String fileName) {
		
		int[][] print = new int[matcherData.size][matcherData.size];
		for (int i = 0; i < matcherData.size; i++) {
			for (int j = 0; j < matcherData.size; j++) {
				print[i][j] = board[matcherData.x + i][matcherData.y + j];
			}
		}
		
		BufferedImage resultImage = ScannerUtils.createGrayImage(print);
		ScannerUtils.saveImage(fileName, resultImage, "png");
	}
}
