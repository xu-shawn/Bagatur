package bagaturchess.bitboard.impl1.internal;


import static bagaturchess.bitboard.impl1.internal.ChessConstants.BISHOP;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.BLACK;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.KING;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.NIGHT;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.PAWN;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.QUEEN;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.ROOK;
import static bagaturchess.bitboard.impl1.internal.ChessConstants.WHITE;

import java.util.Arrays;
import java.util.Random;

import bagaturchess.bitboard.common.Properties;
import bagaturchess.bitboard.impl.utils.VarStatistic;


public final class MoveGenerator {
	
	
	private static final boolean CLEAR_TABLES_ON_NEW_SEARCH 	= true;
	public static boolean USE_COUNTER_MOVES_COUNTS 				= true;
	public static boolean USE_ContinuationHistory 				= false;
	
	private static final boolean BUILD_EXACT_STATS 				= false;
	
	public static final int MOVE_SCORE_SCALE 					= 1000;
	
	
	private static final int LMR_STAT_MULTIPLIER 				= 1 * MOVE_SCORE_SCALE; // Should be at least 1000
	
	//Bigger value of LMR_DEVIATION_MULTIPLIER means less LMR skips.
	//LMR_DEVIATION_MULTIPLIER must be between 0 and 2.
	//The value of LMR_DEVIATION_MULTIPLIER equal to 2, leads to skip of LMR reductions for top 5% of the moves sorted by their LMR rate.
	//We select 5% in order to keep the skip rate in the frame of the standard deviation.
	//Values more than 2 actually means that the LMR optimization is always performed and there are no skips. Value 0 means that it is executed in around 50% of the cases.
	private static final double LMR_DEVIATION_MULTIPLIER 		= 1;
	
	private final long[][] LMR_ALL 								= new long[2][64 * 64];
	private final long[][] LMR_BELOW_ALPHA 						= new long[2][64 * 64];
	private final long[][] LMR_ABOVE_ALPHA 						= new long[2][64 * 64];
	private VarStatistic[] lmrBelowAlphaAVGScores 				= new VarStatistic[2];
	private VarStatistic[] lmrAboveAlphaAVGScores 				= new VarStatistic[2];
	
	
	private static final double LMR_RATE_THREASHOLD_ABOVE_ALPHA = 0.95; //Top X%
	private static final double LMR_RATE_THREASHOLD_BELOW_ALPHA = 0.0001; //Top Y%
	private static final long[][] LMR_STATS_COUNTER_ABOVE_ALPHA = new long[2][LMR_STAT_MULTIPLIER + 1];
	private static final long[][] LMR_STATS_COUNTER_BELOW_ALPHA = new long[2][LMR_STAT_MULTIPLIER + 1];
	private static int[] lmr_rate_all_above_alpha 				= new int[2];
	private static int[] lmr_rate_pointer_above_alpha 			= new int[2];
	private static int[] lmr_rate_all_below_alpha 				= new int[2];
	private static int[] lmr_rate_pointer_below_alpha 			= new int[2];
	
	
	private final int[] moves 									= new int[30000];
	private final long[] moveScores 							= new long[30000];
	private final int[] nextToGenerate 							= new int[EngineConstants.MAX_PLIES * 2];
	private final int[] nextToMove 								= new int[EngineConstants.MAX_PLIES * 2];
	private int currentPly;
	

	private final IBetaCutoffMoves[][] KILLER_MOVES 			= new IBetaCutoffMoves[2][EngineConstants.MAX_PLIES];
	private final IBetaCutoffMoves[][][] COUNTER_MOVES_LASTIN	= new IBetaCutoffMoves[2][7][64];

	private final IBetaCutoffMoves[][][] COUNTER_MOVES_COUNTS	= new IBetaCutoffMoves[2][7][64];
	
	private final long[][] HH_MOVES 							= new long[2][64 * 64];
	private final long[][] BF_MOVES 							= new long[2][64 * 64];
	
	private final long[][][] HH_MOVES1 							= new long[2][7][64];
	private final long[][][] BF_MOVES1 							= new long[2][7][64];
	
	private final ContinuationHistory[][] HH_ContinuationHistory 	= new ContinuationHistory[2][2];
	private final ContinuationHistory[][] BF_ContinuationHistory 	= new ContinuationHistory[2][2];
	
	
	private long counter_sorting;
	
	private Random randomizer = new Random();
	
	private int root_search_first_move_index;
	
	
	public MoveGenerator() {
		
		
		for (int i = 0; i < KILLER_MOVES.length; i++) {
			
			for (int j = 0; j < KILLER_MOVES[i].length; j++) {
				
				KILLER_MOVES[i][j] = new BetaCutoffMoves_LastIn();
			}
		}
		
		for (int i = 0; i < COUNTER_MOVES_LASTIN.length; i++) {
			
			for (int j = 0; j < COUNTER_MOVES_LASTIN[i].length; j++) {
				
				for (int k = 0; k < COUNTER_MOVES_LASTIN[i][j].length; k++) {
					
					COUNTER_MOVES_LASTIN[i][j][k] = new BetaCutoffMoves_LastIn();
				}
			}
		}
		
		if (USE_COUNTER_MOVES_COUNTS) {

			for (int i = 0; i < COUNTER_MOVES_COUNTS.length; i++) {

				for (int j = 0; j < COUNTER_MOVES_COUNTS[i].length; j++) {

					for (int k = 0; k < COUNTER_MOVES_COUNTS[i][j].length; k++) {

						COUNTER_MOVES_COUNTS[i][j][k] = new BetaCutoffMoves_Counts();
					}
				}
			}
		}
		
		
		if (USE_ContinuationHistory) {
			
			HH_ContinuationHistory[0][WHITE] = new ContinuationHistory();
			HH_ContinuationHistory[1][WHITE] = new ContinuationHistory();
			HH_ContinuationHistory[0][BLACK] = new ContinuationHistory();
			HH_ContinuationHistory[1][BLACK] = new ContinuationHistory();
			
			BF_ContinuationHistory[0][WHITE] = new ContinuationHistory();
			BF_ContinuationHistory[1][WHITE] = new ContinuationHistory();
			BF_ContinuationHistory[0][BLACK] = new ContinuationHistory();
			BF_ContinuationHistory[1][BLACK] = new ContinuationHistory();
		}
		
		
		lmrBelowAlphaAVGScores[WHITE] = new VarStatistic();
		lmrBelowAlphaAVGScores[BLACK] = new VarStatistic();
		
		lmrAboveAlphaAVGScores[WHITE] = new VarStatistic();
		lmrAboveAlphaAVGScores[BLACK] = new VarStatistic();
		
		
		clearHistoryHeuristics();
	}
	
	
	public void clearHistoryHeuristics() {
		
		Arrays.fill(HH_MOVES[WHITE], 0);
		Arrays.fill(HH_MOVES[BLACK], 0);
		Arrays.fill(BF_MOVES[WHITE], 1);
		Arrays.fill(BF_MOVES[BLACK], 1);
		
		Arrays.fill(HH_MOVES1[WHITE][0], 0);
		Arrays.fill(HH_MOVES1[WHITE][PAWN], 0);
		Arrays.fill(HH_MOVES1[WHITE][NIGHT], 0);
		Arrays.fill(HH_MOVES1[WHITE][BISHOP], 0);
		Arrays.fill(HH_MOVES1[WHITE][ROOK], 0);
		Arrays.fill(HH_MOVES1[WHITE][QUEEN], 0);
		Arrays.fill(HH_MOVES1[WHITE][KING], 0);
		
		Arrays.fill(HH_MOVES1[BLACK][0], 0);
		Arrays.fill(HH_MOVES1[BLACK][PAWN], 0);
		Arrays.fill(HH_MOVES1[BLACK][NIGHT], 0);
		Arrays.fill(HH_MOVES1[BLACK][BISHOP], 0);
		Arrays.fill(HH_MOVES1[BLACK][ROOK], 0);
		Arrays.fill(HH_MOVES1[BLACK][QUEEN], 0);
		Arrays.fill(HH_MOVES1[BLACK][KING], 0);
		
		Arrays.fill(BF_MOVES1[WHITE][0], 1);
		Arrays.fill(BF_MOVES1[WHITE][PAWN], 1);
		Arrays.fill(BF_MOVES1[WHITE][NIGHT], 1);
		Arrays.fill(BF_MOVES1[WHITE][BISHOP], 1);
		Arrays.fill(BF_MOVES1[WHITE][ROOK], 1);
		Arrays.fill(BF_MOVES1[WHITE][QUEEN], 1);
		Arrays.fill(BF_MOVES1[WHITE][KING], 1);
		
		Arrays.fill(BF_MOVES1[BLACK][0], 1);
		Arrays.fill(BF_MOVES1[BLACK][PAWN], 1);
		Arrays.fill(BF_MOVES1[BLACK][NIGHT], 1);
		Arrays.fill(BF_MOVES1[BLACK][BISHOP], 1);
		Arrays.fill(BF_MOVES1[BLACK][ROOK], 1);
		Arrays.fill(BF_MOVES1[BLACK][QUEEN], 1);
		Arrays.fill(BF_MOVES1[BLACK][KING], 1);	
		
		
		if (CLEAR_TABLES_ON_NEW_SEARCH) {

			//No sense to clear it as they are last in and will be overided from the new search moves
			/*for (int i = 0; i < COUNTER_MOVES_LASTIN.length; i++) {
			
			for (int j = 0; j < COUNTER_MOVES_LASTIN[i].length; j++) {
				
				for (int k = 0; k < COUNTER_MOVES_LASTIN[i][j].length; k++) {
					
					COUNTER_MOVES_LASTIN[i][j][k].clear();
				}
			}
			}*/
			
			if (USE_COUNTER_MOVES_COUNTS) {

				for (int i = 0; i < COUNTER_MOVES_COUNTS.length; i++) {

					for (int j = 0; j < COUNTER_MOVES_COUNTS[i].length; j++) {

						for (int k = 0; k < COUNTER_MOVES_COUNTS[i][j].length; k++) {

							COUNTER_MOVES_COUNTS[i][j][k].clear();
						}
					}
				}
			}
			
			//Do not clear it, as in version 3.0 which is the strongest this structure is permanent
			/*if (USE_ContinuationHistory) {
				
				HH_ContinuationHistory[WHITE].clear();
				HH_ContinuationHistory[BLACK].clear();
				
				BF_ContinuationHistory[WHITE].clear();
				BF_ContinuationHistory[BLACK].clear();
			}*/
		}
		
		Arrays.fill(LMR_ALL[WHITE], 0);
		Arrays.fill(LMR_ALL[BLACK], 0);
		Arrays.fill(LMR_BELOW_ALPHA[WHITE], 0);
		Arrays.fill(LMR_BELOW_ALPHA[BLACK], 0);
		Arrays.fill(LMR_ABOVE_ALPHA[WHITE], 0);
		Arrays.fill(LMR_ABOVE_ALPHA[BLACK], 0);
		Arrays.fill(LMR_STATS_COUNTER_ABOVE_ALPHA[WHITE], 0);
		Arrays.fill(LMR_STATS_COUNTER_ABOVE_ALPHA[BLACK], 0);
		Arrays.fill(LMR_STATS_COUNTER_BELOW_ALPHA[WHITE], 0);
		Arrays.fill(LMR_STATS_COUNTER_BELOW_ALPHA[BLACK], 0);
		Arrays.fill(lmr_rate_all_above_alpha, 0);
		Arrays.fill(lmr_rate_pointer_above_alpha, 0);
		Arrays.fill(lmr_rate_all_below_alpha, 0);
		Arrays.fill(lmr_rate_pointer_below_alpha, 0);
		
		lmrBelowAlphaAVGScores[WHITE].clear();
		lmrBelowAlphaAVGScores[BLACK].clear();
		lmrAboveAlphaAVGScores[WHITE].clear();
		lmrAboveAlphaAVGScores[BLACK].clear();
		
		
		currentPly = 0;
	}
	
	
	public void setRootSearchFirstMoveIndex(int _root_search_first_move_index) {
		
		root_search_first_move_index = _root_search_first_move_index;
	}
	
	
	public void addHHValue(final int inCheck, final int color, final int move, final int parentMove, final int depth) {
		HH_MOVES[color][MoveUtil.getFromToIndex(move)] += depth * depth;
		HH_MOVES1[color][MoveUtil.getSourcePieceIndex(move)][MoveUtil.getToIndex(move)] += depth * depth;
		if (USE_ContinuationHistory) HH_ContinuationHistory[inCheck][color == WHITE ? BLACK : WHITE].array[MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].array[MoveUtil.getSourcePieceIndex(move)][MoveUtil.getToIndex(move)] += depth * depth;
	}
	
	
	public void addBFValue(final int inCheck, final int color, final int move, final int parentMove, final int depth) {
		BF_MOVES[color][MoveUtil.getFromToIndex(move)] += depth * depth;
		BF_MOVES1[color][MoveUtil.getSourcePieceIndex(move)][MoveUtil.getToIndex(move)] += depth * depth;
		if (USE_ContinuationHistory) BF_ContinuationHistory[inCheck][color == WHITE ? BLACK : WHITE].array[MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].array[MoveUtil.getSourcePieceIndex(move)][MoveUtil.getToIndex(move)] += depth * depth;
	}
	
	
	public int getHHScore(final int inCheck, final int color, final int fromToIndex, final int pieceType, final int toIndex, final int parentMove) {
		
		int value3 = (int) (USE_ContinuationHistory ? getContinuationHistoryScore(inCheck, color, pieceType, toIndex, parentMove) : 0);
		
		if (USE_ContinuationHistory) {
			
			return value3;
			
		} else {

			int value1 = (int) (MOVE_SCORE_SCALE * HH_MOVES[color][fromToIndex] / BF_MOVES[color][fromToIndex]);
			int value2 = (int) (MOVE_SCORE_SCALE * HH_MOVES1[color][pieceType][toIndex] / BF_MOVES1[color][pieceType][toIndex]);

			return Math.max(value1, Math.max(value2, value3));
		}
		
		//return (value1 + value2 + value3) / 3;
	}
	
	
	private long getContinuationHistoryScore(final int inCheck, final int color, final int pieceType, final int toIndex, final int parentMove) {
		return MOVE_SCORE_SCALE * HH_ContinuationHistory[inCheck][color == WHITE ? BLACK : WHITE].array[MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].array[pieceType][toIndex] / 
				BF_ContinuationHistory[inCheck][color == WHITE ? BLACK : WHITE].array[MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].array[pieceType][toIndex];
	}
	
	
	public void addLMR_All(final int color, final int move, final int depth) {
		
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return;
		}
		
		
		LMR_ALL[color][MoveUtil.getFromToIndex(move)] += depth * depth;
	}
	
	
	public void addLMR_AboveAlpha(final int color, final int move, final int depth) {
		
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return;
		}
		
		
		int fromToIndex = MoveUtil.getFromToIndex(move);
		
		LMR_ABOVE_ALPHA[color][fromToIndex] += depth * depth;
		
		
		if (EngineConstants.ENABLE_LMR_STATS_DECISION || EngineConstants.ENABLE_LMP_STATS_DECISION) {
			
			int rate = getLMR_Rate_internal(color, fromToIndex);
			
			lmrAboveAlphaAVGScores[color].addValue(rate);
		}
		
		
		if (BUILD_EXACT_STATS) {
			
			int rate = getLMR_Rate_internal(color, fromToIndex);
			
			//System.out.println("addLMR_AboveAlpha: COLOR " + color + ", LMR_RATE_THREASHOLD_ABOVE_ALPHA=" + LMR_RATE_THREASHOLD_ABOVE_ALPHA + " POINTER=" + getLMR_ThreasholdPointer_AboveAlpha(color));
			
			LMR_STATS_COUNTER_ABOVE_ALPHA[color][rate]++;
			lmr_rate_all_above_alpha[color]++;
			
			int sum = 0;
			int pointer = LMR_STAT_MULTIPLIER - 1;
			while (pointer > 0 && sum / (double) lmr_rate_all_above_alpha[color] < LMR_RATE_THREASHOLD_ABOVE_ALPHA) {
				sum += LMR_STATS_COUNTER_ABOVE_ALPHA[color][pointer];
				pointer--;
			}
			
			lmr_rate_pointer_above_alpha[color] = pointer;
		}
	}
	
	
	public void addLMR_BelowAlpha(final int color, final int move, final int depth) {
		
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return;
		}
		
		
		int fromToIndex = MoveUtil.getFromToIndex(move);
		
		LMR_BELOW_ALPHA[color][fromToIndex] += depth * depth;
		
		
		if (EngineConstants.ENABLE_LMR_STATS_DECISION || EngineConstants.ENABLE_LMP_STATS_DECISION) {
			
			int rate = getLMR_Rate_internal(color, fromToIndex);
			
			lmrBelowAlphaAVGScores[color].addValue(rate);
		}
		
		
		if (BUILD_EXACT_STATS) {
			
			int rate = getLMR_Rate_internal(color, fromToIndex);
			
			//System.out.println("addLMR_BelowAlpha: COLOR " + color + ", LMR_RATE_THREASHOLD_BELOW_ALPHA=" + LMR_RATE_THREASHOLD_BELOW_ALPHA + " POINTER=" + getLMR_ThreasholdPointer_BelowAlpha(color));
			
			LMR_STATS_COUNTER_BELOW_ALPHA[color][rate]++;
			lmr_rate_all_below_alpha[color]++;
			
			int sum = 0;
			int pointer = LMR_STAT_MULTIPLIER - 1;
			while (pointer > 0 && sum / (double) lmr_rate_all_below_alpha[color] < LMR_RATE_THREASHOLD_BELOW_ALPHA) {
				sum += LMR_STATS_COUNTER_BELOW_ALPHA[color][pointer];
				pointer--;
			}
			
			lmr_rate_pointer_below_alpha[color] = pointer;
		}
	}
	
	
	public int getLMR_Rate(final int color, final int move) {		
		
		return getLMR_Rate_internal(color, MoveUtil.getFromToIndex(move));
	}
	
	
	private int getLMR_Rate_internal(final int color, final int fromToIndex) {
		
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return 0;
		}
		
		
		long count_all = LMR_ALL[color][fromToIndex];
		
		if (count_all == 0) {
			
			return 0;
		}
		
		return (int) ((LMR_STAT_MULTIPLIER * (count_all - LMR_BELOW_ALPHA[color][fromToIndex])) / count_all);
	}
	
	
	public int getLMR_ThreasholdPointer_AboveAlpha(int color) {
		
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return 0;
		}
		
		
		if (BUILD_EXACT_STATS) {
			
			return lmr_rate_pointer_above_alpha[color];
			
		} else {
			
			if (EngineConstants.ENABLE_LMR_STATS_DECISION || EngineConstants.ENABLE_LMP_STATS_DECISION) {
				
				int pointer_above_alpha = (int) (lmrAboveAlphaAVGScores[color].getEntropy() + LMR_DEVIATION_MULTIPLIER * lmrAboveAlphaAVGScores[color].getDisperse());
				
				//System.out.println("AboveAlpha: color=" + color + ", Entropy=" + lmrAboveAlphaAVGScores[color].getEntropy() + ", Disperse=" + lmrAboveAlphaAVGScores[color].getDisperse());
				
				return pointer_above_alpha;
				
			} else {
				
				return 0;
			}
		}
	}
	
	
	public int getLMR_ThreasholdPointer_BelowAlpha(int color) {
		
		if (!EngineConstants.ENABLE_LMR_STATS) {
			
			return 0;
		}
		
		if (BUILD_EXACT_STATS) {
			
			return lmr_rate_pointer_below_alpha[color];
			
		} else {
		
			if (EngineConstants.ENABLE_LMR_STATS_DECISION || EngineConstants.ENABLE_LMP_STATS_DECISION) {
			
				int pointer_below_alpha = (int) (lmrBelowAlphaAVGScores[color].getEntropy() + LMR_DEVIATION_MULTIPLIER * lmrBelowAlphaAVGScores[color].getDisperse());
				
				//System.out.println("BelowAlpha: color=" + color + ", Entropy=" + lmrBelowAlphaAVGScores[color].getEntropy() + ", Disperse=" + lmrBelowAlphaAVGScores[color].getDisperse());
				
				return pointer_below_alpha;
				
			} else {
				
				return 0;
			}
		}
	}
	
	
	public void addKillerMove(final int color, final int move, final int ply) {
		
		if (EngineConstants.ENABLE_KILLER_MOVES) {
			
			KILLER_MOVES[color][ply].addMove(move);
		}
	}
	
	
	public void addCounterMove(final int color, final int parentMove, final int counterMove) {
		
		if (EngineConstants.ENABLE_COUNTER_MOVES) {
			
			COUNTER_MOVES_LASTIN[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].addMove(counterMove);

			if (USE_COUNTER_MOVES_COUNTS) {

				COUNTER_MOVES_COUNTS[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].addMove(counterMove);
			}
		}
	}
	

	public int getCounter1(final int color, final int parentMove) {
		
		return COUNTER_MOVES_LASTIN[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].getBest1();
		//return COUNTER_MOVES_COUNTS[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].getBest1();
	}
	
	
	public int getCounter2(final int color, final int parentMove) {

		if (!USE_COUNTER_MOVES_COUNTS) {

			return 0;
		}

		return COUNTER_MOVES_COUNTS[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].getBest1();
		//return COUNTER_MOVES_LASTIN[color][MoveUtil.getSourcePieceIndex(parentMove)][MoveUtil.getToIndex(parentMove)].getBest1();
	}
	
	
	public int getKiller1(final int color, final int ply) {
		
		return KILLER_MOVES[color][ply].getBest1();
	}

	
	public int getKiller2(final int color, final int ply) {
		
		return KILLER_MOVES[color][ply].getBest2();
	}
	
	
	public void startPly() {
		nextToGenerate[currentPly + 1] = nextToGenerate[currentPly];
		nextToMove[currentPly + 1] = nextToGenerate[currentPly];
		currentPly++;
	}

	
	public void endPly() {
		currentPly--;
	}

	
	public int next() {
		return moves[nextToMove[currentPly]++];
	}

	public long getScore() {
		
		long val = moveScores[nextToMove[currentPly] - 1];
		
		if (val < 0) {
			
			throw new IllegalStateException("getScore: val=" + val);
		}
		
		return val;
	}

	public int previous() {
		if (nextToMove[currentPly] - 1 < 0) {
			return 0;
		}
		
		return moves[nextToMove[currentPly] - 1];
	}

	public boolean hasNext() {
		return nextToGenerate[currentPly] != nextToMove[currentPly];
	}

	public void addMove(final int move) {
		moves[nextToGenerate[currentPly]++] = move;
	}

	public void setMVVLVAScores(final ChessBoard cb) {
		
		final int scale = 100;
		
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			
			int move = moves[j];
			
			//getAttackedPieceIndex and getSourcePieceIndex returns value in [1, 6]
			
			int score = (6 * MoveUtil.getAttackedPieceIndex(move) - 1 * MoveUtil.getSourcePieceIndex(move));
			
			if (MoveUtil.isPromotion(move)) {
				
				//MoveUtil.getMoveType(move) returns value in [2, 5] when the move is promotion
				score += 1 * MoveUtil.getMoveType(move);
				
			}
			
			moveScores[j] = scale * score;
		}
	}
	
	public void setSEEScores(final ChessBoard cb) {
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			moveScores[j] = SEEUtil.getSeeCaptureScore(cb, moves[j]);
		}
	}
	
	public int getCountGoodAttacks(final ChessBoard cb) {
		int count = 0;
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			if (SEEUtil.getSeeCaptureScore(cb, moves[j]) > 0) count++;
		}
		return count;
	}
	
	
	public int getCountEqualAttacks(final ChessBoard cb) {
		int count = 0;
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			if (SEEUtil.getSeeCaptureScore(cb, moves[j]) == 0) count++;
		}
		return count;
	}
	
	
	public int getCountBadAttacks(final ChessBoard cb) {
		int count = 0;
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			if (SEEUtil.getSeeCaptureScore(cb, moves[j]) < 0) count++;
		}
		return count;
	}
	
	
	public int getCountGoodAndEqualAttacks(final ChessBoard cb) {
		int count = 0;
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			if (SEEUtil.getSeeCaptureScore(cb, moves[j]) >= 0) count++;
		}
		return count;
	}
	
	
	public int getCountMoves() {
		return nextToGenerate[currentPly] - nextToMove[currentPly];
	}
	
	
	public void setHHScores(final int inCheck, final int colorToMove, final int parentMove) {
		
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			
			int move = moves[j];
			
			int from_to_index = MoveUtil.getFromToIndex(move);
			
			//long score = 0;
			long score = getHHScore(inCheck, colorToMove, from_to_index, MoveUtil.getSourcePieceIndex(move), MoveUtil.getToIndex(move), parentMove);
			
			/**
			 * Each particular move's score is calculated by the ration between the beta cutoffs occurrences after this move divided by the number of all occurrences of the move.
			 * The cutoffs statistics are measured only by moves performed by LMR at shallow depths.
			 * If we base the move ordering on this scores, we actually are increasing the probability of cutoffs in the whole search tree on an optimal depth, which means the PV should be found in a optimal way from time perspective.
			 * This approach should be kind of dynamic optimization as all other heuristic here are.
			 * I am happy that this approach doesn't makes the search speed worse.
			 * */
			score += getLMR_Rate_internal(colorToMove, from_to_index);
			//score += 0;
			
			if (score < 0) {
				
				throw new IllegalStateException("score < 0");
			}
			
			moveScores[j] = score;
		}
	}
	
	
	public void setRootScores(final ChessBoard cb, final int parentMove, final int ttMove, final int ply) {
		
		int killer1Move = getKiller1(cb.colorToMove, ply);
		int killer2Move = getKiller2(cb.colorToMove, ply);
		int counterMove1 = getCounter1(cb.colorToMove, parentMove);
		int counterMove2 = getCounter2(cb.colorToMove, parentMove);
		
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			
			int cur_move = moves[j];
			
			if (ttMove == cur_move) {
				
				moveScores[j] = 10000 * 100;
			
			} else if (killer1Move == cur_move) {
				
				moveScores[j] = 5000 * 100;
				
			} else if (killer2Move == cur_move) {
				
				moveScores[j] = 4000 * 100;
				
			} else if (counterMove1 == cur_move) {
				
				moveScores[j] = 3000 * 100;
				
			} else if (counterMove2 == cur_move) {
				
				moveScores[j] = 2000 * 100;
				
			} else if (MoveUtil.isQuiet(cur_move)) {
				
				moveScores[j] = getHHScore(cb.checkingPieces == 0 ? 0 : 1, cb.colorToMove, MoveUtil.getFromToIndex(cur_move), MoveUtil.getSourcePieceIndex(cur_move), MoveUtil.getToIndex(cur_move), parentMove);
				
			} else {
				
				if (SEEUtil.getSeeCaptureScore(cb, cur_move) >= 0) {
				
					moveScores[j] = 7000 * 100 + 100 * (MoveUtil.getAttackedPieceIndex(cur_move) * 6 - MoveUtil.getSourcePieceIndex(cur_move));
					
				} else {
					
					moveScores[j] = -5000 + 100 * (MoveUtil.getAttackedPieceIndex(cur_move) * 6 - MoveUtil.getSourcePieceIndex(cur_move));
				}
			}
		}
	}
	
	
	/*public void setAllScores(final ChessBoard cb, final int parentMove, final int ttMove, int counterMove, int killer1Move, int killer2Move) {
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			if (ttMove == moves[j]) {
				moveScores[j] = 10000;
			} else if (counterMove == moves[j]) {
				moveScores[j] = 300;
			} else if (killer1Move == moves[j]) {
				moveScores[j] = 500;
			} else if (killer2Move == moves[j]) {
				moveScores[j] = 400;
			} else if (!MoveUtil.isQuiet(moves[j])) {
				moveScores[j] = 1000 + SEEUtil.getSeeCaptureScore(cb, moves[j]);
			} else {
				moveScores[j] = getHHScore(cb.colorToMove, MoveUtil.getFromToIndex(moves[j]), MoveUtil.getSourcePieceIndex(moves[j]), MoveUtil.getToIndex(moves[j]), parentMove);
			}
			//System.out.println("moveScores[j]=" + moveScores[j]);
		}
	}*/
	
	
	public void sort() {
		
		
		final int start_index = nextToMove[currentPly];
		final int end_index = nextToGenerate[currentPly] - 1;
		
		//In order to increase the effect of nondeterminism, ensure first ordering is randomized.
		if (counter_sorting == 0 || counter_sorting % 10 == 0) {
				
			randomize(moveScores, moves, start_index, end_index);
		}
		
		
		for (int i = start_index, j = i; i < end_index; j = ++i) {
			final long score = moveScores[i + 1];
			final int move = moves[i + 1];
			while (score > moveScores[j]) {
				moveScores[j + 1] = moveScores[j];
				moves[j + 1] = moves[j];
				if (j-- == start_index) {
					break;
				}
			}
			moveScores[j + 1] = score;
			moves[j + 1] = move;
		}
		
		
		//The ELO is smaller with this code enabled. It looks like it is better when all threads start searching the TT move.
		//My explanation is that, they do enough randomization (in this method: randomize(...) before sorting) of the moves with the same scores, so they work in different sub-trees anyway and 
		//as the TT move is most probably the best one, the SMP version goes a few moves deeper for the same time.
		if (false && currentPly == 1) {
		
			//Lazy SMP logic
			//currentPly == 1 (not 0), because first we make currentPly++ and then call sort method
			
			int current_moves_count = (end_index - start_index + 1);
			
			if (current_moves_count >= 2) {
				
				int index_to_swap = root_search_first_move_index % current_moves_count;
				
				long score 									= moveScores[start_index + index_to_swap];
				int move 									= moves[start_index + index_to_swap];
				
				moveScores[start_index + index_to_swap] 	= moveScores[start_index];
				moves[start_index + index_to_swap] 			= moves[start_index];
				
				moveScores[start_index] 					= score;
				moves[start_index] 							= move;
			}
		}
		
		
		counter_sorting++;
	}
	
	
	private void randomize(long[] arr1, int[] arr2, int start, int end) {
		
	    for (int i = end; i > start + 1; i--) {
	    	
	    	int rnd_index = start + randomizer.nextInt(i - start);	    
	    	
	    	long tmp1 = arr1[i-1];
	    	arr1[i-1] = arr1[rnd_index];
	    	arr1[rnd_index] = tmp1;
	    	
	    	int tmp2 = arr2[i-1];
	    	arr2[i-1] = arr2[rnd_index];
	    	arr2[rnd_index] = tmp2;
	    }
	}
	

	/*public String getMovesAsString() {
		StringBuilder sb = new StringBuilder();
		for (int j = nextToMove[currentPly]; j < nextToGenerate[currentPly]; j++) {
			sb.append(new MoveWrapper(moves[j]) + ", ");
		}
		return sb.toString();
	}
	*/
	
	
	
	
	/**
	 * Moves generation
	 */
	
	
	public void generateMoves(final ChessBoard cb) {

		switch (Long.bitCount(cb.checkingPieces)) {
		case 0:
			// not in-check
			generateNotInCheckMoves(cb);
			break;
		case 1:
			// in-check
			switch (cb.pieceIndexes[Long.numberOfTrailingZeros(cb.checkingPieces)]) {
			case PAWN:
				// fall-through
			case NIGHT:
				// move king
				addKingMoves(cb);
				break;
			default:
				generateOutOfSlidingCheckMoves(cb);
			}
			break;
		default:
			// double check, only the king can move
			addKingMoves(cb);
		}
	}

	public void generateAttacks(final ChessBoard cb) {

		switch (Long.bitCount(cb.checkingPieces)) {
		case 0:
			// not in-check
			generateNotInCheckAttacks(cb);
			break;
		case 1:
			generateOutOfCheckAttacks(cb);
			break;
		default:
			// double check, only the king can attack
			addKingAttacks(cb);
		}
	}

	private void generateNotInCheckMoves(final ChessBoard cb) {

		// non pinned pieces
		addKingMoves(cb);
		addQueenMoves(cb.pieces[cb.colorToMove][QUEEN] & ~cb.pinnedPieces, cb.allPieces, cb.emptySpaces);
		addRookMoves(cb.pieces[cb.colorToMove][ROOK] & ~cb.pinnedPieces, cb.allPieces, cb.emptySpaces);
		addBishopMoves(cb.pieces[cb.colorToMove][BISHOP] & ~cb.pinnedPieces, cb.allPieces, cb.emptySpaces);
		addNightMoves(cb.pieces[cb.colorToMove][NIGHT] & ~cb.pinnedPieces, cb.emptySpaces);
		addPawnMoves(cb.pieces[cb.colorToMove][PAWN] & ~cb.pinnedPieces, cb, cb.emptySpaces);

		// pinned pieces
		long piece = cb.friendlyPieces[cb.colorToMove] & cb.pinnedPieces;
		while (piece != 0) {
			switch (cb.pieceIndexes[Long.numberOfTrailingZeros(piece)]) {
			case PAWN:
				addPawnMoves(Long.lowestOneBit(piece), cb,
						cb.emptySpaces & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
				break;
			case BISHOP:
				addBishopMoves(Long.lowestOneBit(piece), cb.allPieces,
						cb.emptySpaces & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
				break;
			case ROOK:
				addRookMoves(Long.lowestOneBit(piece), cb.allPieces,
						cb.emptySpaces & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
				break;
			case QUEEN:
				addQueenMoves(Long.lowestOneBit(piece), cb.allPieces,
						cb.emptySpaces & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
			}
			piece &= piece - 1;
		}

	}

	private void generateOutOfSlidingCheckMoves(final ChessBoard cb) {

		// TODO when check is blocked -> pinned piece

		// move king or block sliding piece
		final long inBetween = ChessConstants.IN_BETWEEN[cb.kingIndex[cb.colorToMove]][Long.numberOfTrailingZeros(cb.checkingPieces)];
		if (inBetween != 0) {
			addNightMoves(cb.pieces[cb.colorToMove][NIGHT] & ~cb.pinnedPieces, inBetween);
			addBishopMoves(cb.pieces[cb.colorToMove][BISHOP] & ~cb.pinnedPieces, cb.allPieces, inBetween);
			addRookMoves(cb.pieces[cb.colorToMove][ROOK] & ~cb.pinnedPieces, cb.allPieces, inBetween);
			addQueenMoves(cb.pieces[cb.colorToMove][QUEEN] & ~cb.pinnedPieces, cb.allPieces, inBetween);
			addPawnMoves(cb.pieces[cb.colorToMove][PAWN] & ~cb.pinnedPieces, cb, inBetween);
		}

		addKingMoves(cb);
	}

	private void generateNotInCheckAttacks(final ChessBoard cb) {

		final long enemies = cb.friendlyPieces[cb.colorToMoveInverse];

		// non pinned pieces
		addEpAttacks(cb);
		addPawnAttacksAndPromotions(cb.pieces[cb.colorToMove][PAWN] & ~cb.pinnedPieces, cb, enemies, cb.emptySpaces);
		addNightAttacks(cb.pieces[cb.colorToMove][NIGHT] & ~cb.pinnedPieces, cb.pieceIndexes, enemies);
		addRookAttacks(cb.pieces[cb.colorToMove][ROOK] & ~cb.pinnedPieces, cb, enemies);
		addBishopAttacks(cb.pieces[cb.colorToMove][BISHOP] & ~cb.pinnedPieces, cb, enemies);
		addQueenAttacks(cb.pieces[cb.colorToMove][QUEEN] & ~cb.pinnedPieces, cb, enemies);
		addKingAttacks(cb);

		// pinned pieces
		long piece = cb.friendlyPieces[cb.colorToMove] & cb.pinnedPieces;
		while (piece != 0) {
			switch (cb.pieceIndexes[Long.numberOfTrailingZeros(piece)]) {
			case PAWN:
				addPawnAttacksAndPromotions(Long.lowestOneBit(piece), cb,
						enemies & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]], 0);
				break;
			case BISHOP:
				addBishopAttacks(Long.lowestOneBit(piece), cb,
						enemies & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
				break;
			case ROOK:
				addRookAttacks(Long.lowestOneBit(piece), cb,
						enemies & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
				break;
			case QUEEN:
				addQueenAttacks(Long.lowestOneBit(piece), cb,
						enemies & ChessConstants.PINNED_MOVEMENT[Long.numberOfTrailingZeros(piece)][cb.kingIndex[cb.colorToMove]]);
			}
			piece &= piece - 1;
		}

	}

	private void generateOutOfCheckAttacks(final ChessBoard cb) {
		// attack attacker
		addEpAttacks(cb);
		addPawnAttacksAndPromotions(cb.pieces[cb.colorToMove][PAWN] & ~cb.pinnedPieces, cb, cb.checkingPieces, cb.emptySpaces);
		addNightAttacks(cb.pieces[cb.colorToMove][NIGHT] & ~cb.pinnedPieces, cb.pieceIndexes, cb.checkingPieces);
		addBishopAttacks(cb.pieces[cb.colorToMove][BISHOP] & ~cb.pinnedPieces, cb, cb.checkingPieces);
		addRookAttacks(cb.pieces[cb.colorToMove][ROOK] & ~cb.pinnedPieces, cb, cb.checkingPieces);
		addQueenAttacks(cb.pieces[cb.colorToMove][QUEEN] & ~cb.pinnedPieces, cb, cb.checkingPieces);
		addKingAttacks(cb);
	}

	private void addPawnAttacksAndPromotions(final long pawns, final ChessBoard cb, final long enemies, final long emptySpaces) {

		if (pawns == 0) {
			return;
		}

		if (cb.colorToMove == WHITE) {

			// non-promoting
			long piece = pawns & Bitboard.RANK_NON_PROMOTION[WHITE] & Bitboard.getBlackPawnAttacks(enemies);
			while (piece != 0) {
				final int fromIndex = Long.numberOfTrailingZeros(piece);
				long moves = StaticMoves.PAWN_ATTACKS[WHITE][fromIndex] & enemies;
				while (moves != 0) {
					final int toIndex = Long.numberOfTrailingZeros(moves);
					addMove(MoveUtil.createAttackMove(fromIndex, toIndex, PAWN, cb.pieceIndexes[toIndex]));
					moves &= moves - 1;
				}
				piece &= piece - 1;
			}

			// promoting
			piece = pawns & Bitboard.RANK_7;
			while (piece != 0) {
				final int fromIndex = Long.numberOfTrailingZeros(piece);

				// promotion move
				if ((Long.lowestOneBit(piece) << 8 & emptySpaces) != 0) {
					addPromotionMove(fromIndex, fromIndex + 8);
				}

				// promotion attacks
				addPromotionAttacks(StaticMoves.PAWN_ATTACKS[WHITE][fromIndex] & enemies, fromIndex, cb.pieceIndexes);

				piece &= piece - 1;
			}
		} else {
			// non-promoting
			long piece = pawns & Bitboard.RANK_NON_PROMOTION[BLACK] & Bitboard.getWhitePawnAttacks(enemies);
			while (piece != 0) {
				final int fromIndex = Long.numberOfTrailingZeros(piece);
				long moves = StaticMoves.PAWN_ATTACKS[BLACK][fromIndex] & enemies;
				while (moves != 0) {
					final int toIndex = Long.numberOfTrailingZeros(moves);
					addMove(MoveUtil.createAttackMove(fromIndex, toIndex, PAWN, cb.pieceIndexes[toIndex]));
					moves &= moves - 1;
				}
				piece &= piece - 1;
			}

			// promoting
			piece = pawns & Bitboard.RANK_2;
			while (piece != 0) {
				final int fromIndex = Long.numberOfTrailingZeros(piece);

				// promotion move
				if ((Long.lowestOneBit(piece) >>> 8 & emptySpaces) != 0) {
					addPromotionMove(fromIndex, fromIndex - 8);
				}

				// promotion attacks
				addPromotionAttacks(StaticMoves.PAWN_ATTACKS[BLACK][fromIndex] & enemies, fromIndex, cb.pieceIndexes);

				piece &= piece - 1;
			}
		}
	}

	private void addBishopAttacks(long piece, final ChessBoard cb, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getBishopMoves(fromIndex, cb.allPieces) & possiblePositions;
			while (moves != 0) {
				final int toIndex = Long.numberOfTrailingZeros(moves);
				addMove(MoveUtil.createAttackMove(fromIndex, toIndex, BISHOP, cb.pieceIndexes[toIndex]));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addRookAttacks(long piece, final ChessBoard cb, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getRookMoves(fromIndex, cb.allPieces) & possiblePositions;
			while (moves != 0) {
				final int toIndex = Long.numberOfTrailingZeros(moves);
				addMove(MoveUtil.createAttackMove(fromIndex, toIndex, ROOK, cb.pieceIndexes[toIndex]));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addQueenAttacks(long piece, final ChessBoard cb, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getQueenMoves(fromIndex, cb.allPieces) & possiblePositions;
			while (moves != 0) {
				final int toIndex = Long.numberOfTrailingZeros(moves);
				addMove(MoveUtil.createAttackMove(fromIndex, toIndex, QUEEN, cb.pieceIndexes[toIndex]));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addBishopMoves(long piece, final long allPieces, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getBishopMoves(fromIndex, allPieces) & possiblePositions;
			while (moves != 0) {
				addMove(MoveUtil.createMove(fromIndex, Long.numberOfTrailingZeros(moves), BISHOP));
				moves &= moves - 1;
			}

			piece &= piece - 1;
		}
	}

	private void addQueenMoves(long piece, final long allPieces, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getQueenMoves(fromIndex, allPieces) & possiblePositions;
			while (moves != 0) {
				addMove(MoveUtil.createMove(fromIndex, Long.numberOfTrailingZeros(moves), QUEEN));
				moves &= moves - 1;
			}

			piece &= piece - 1;
		}
	}

	private void addRookMoves(long piece, final long allPieces, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = MagicUtil.getRookMoves(fromIndex, allPieces) & possiblePositions;
			while (moves != 0) {
				addMove(MoveUtil.createMove(fromIndex, Long.numberOfTrailingZeros(moves), ROOK));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addNightMoves(long piece, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = StaticMoves.KNIGHT_MOVES[fromIndex] & possiblePositions;
			while (moves != 0) {
				addMove(MoveUtil.createMove(fromIndex, Long.numberOfTrailingZeros(moves), NIGHT));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addPawnMoves(final long pawns, final ChessBoard cb, final long possiblePositions) {

		if (pawns == 0) {
			return;
		}

		if (cb.colorToMove == WHITE) {
			// 1-move
			long piece = pawns & (possiblePositions >>> 8) & Bitboard.RANK_23456;
			while (piece != 0) {
				addMove(MoveUtil.createWhitePawnMove(Long.numberOfTrailingZeros(piece)));
				piece &= piece - 1;
			}
			// 2-move
			piece = pawns & (possiblePositions >>> 16) & Bitboard.RANK_2;
			while (piece != 0) {
				if ((cb.emptySpaces & (Long.lowestOneBit(piece) << 8)) != 0) {
					addMove(MoveUtil.createWhitePawn2Move(Long.numberOfTrailingZeros(piece)));
				}
				piece &= piece - 1;
			}
		} else {
			// 1-move
			long piece = pawns & (possiblePositions << 8) & Bitboard.RANK_34567;
			while (piece != 0) {
				addMove(MoveUtil.createBlackPawnMove(Long.numberOfTrailingZeros(piece)));
				piece &= piece - 1;
			}
			// 2-move
			piece = pawns & (possiblePositions << 16) & Bitboard.RANK_7;
			while (piece != 0) {
				if ((cb.emptySpaces & (Long.lowestOneBit(piece) >>> 8)) != 0) {
					addMove(MoveUtil.createBlackPawn2Move(Long.numberOfTrailingZeros(piece)));
				}
				piece &= piece - 1;
			}
		}
	}

	private void addKingMoves(final ChessBoard cb) {
		
		if (Properties.DUMP_CASTLING) System.out.println("MoveGenerator.addKingMoves");
		
		final int fromIndex = cb.kingIndex[cb.colorToMove];
		
		long moves = StaticMoves.KING_MOVES[fromIndex] & cb.emptySpaces;
		
		while (moves != 0) {
			
			addMove(MoveUtil.createMove(fromIndex, Long.numberOfTrailingZeros(moves), KING));
			
			moves &= moves - 1;
		}

		
		// castling
		if (cb.checkingPieces == 0) {
			
			if (Properties.DUMP_CASTLING) System.out.println("MoveGenerator.addKingMoves: cb.colorToMove=" + cb.colorToMove + ", cb.castlingRights=" + cb.castlingRights + ", cb.castlingConfig=" + cb.castlingConfig);
			
			long castlingIndexes = CastlingUtil.getCastlingIndexes(cb.colorToMove, cb.castlingRights, cb.castlingConfig);
			
			if (Properties.DUMP_CASTLING) System.out.println("MoveGenerator.addKingMoves: castlingIndexes=" + castlingIndexes);
			
			while (castlingIndexes != 0) {
				
				final int toIndex_king = Long.numberOfTrailingZeros(castlingIndexes);
				
				if (Properties.DUMP_CASTLING) System.out.println("MoveGenerator.addKingMoves: toIndex_king=" + toIndex_king);
				
				// no piece in between?
				if (CastlingUtil.isValidCastlingMove(cb, fromIndex, toIndex_king)) {
					
					addMove(MoveUtil.createCastlingMove(fromIndex, toIndex_king));
				}
				
				castlingIndexes &= castlingIndexes - 1;
			}
		}
	}

	private void addKingAttacks(final ChessBoard cb) {
		final int fromIndex = cb.kingIndex[cb.colorToMove];
		long moves = StaticMoves.KING_MOVES[fromIndex] & cb.friendlyPieces[cb.colorToMoveInverse];
		while (moves != 0) {
			final int toIndex = Long.numberOfTrailingZeros(moves);
			addMove(MoveUtil.createAttackMove(fromIndex, toIndex, KING, cb.pieceIndexes[toIndex]));
			moves &= moves - 1;
		}
	}

	private void addNightAttacks(long piece, final int[] pieceIndexes, final long possiblePositions) {
		while (piece != 0) {
			final int fromIndex = Long.numberOfTrailingZeros(piece);
			long moves = StaticMoves.KNIGHT_MOVES[fromIndex] & possiblePositions;
			while (moves != 0) {
				final int toIndex = Long.numberOfTrailingZeros(moves);
				addMove(MoveUtil.createAttackMove(fromIndex, toIndex, NIGHT, pieceIndexes[toIndex]));
				moves &= moves - 1;
			}
			piece &= piece - 1;
		}
	}

	private void addEpAttacks(final ChessBoard cb) {
		if (cb.epIndex == 0) {
			return;
		}
		long piece = cb.pieces[cb.colorToMove][PAWN] & StaticMoves.PAWN_ATTACKS[cb.colorToMoveInverse][cb.epIndex];
		while (piece != 0) {
			addMove(MoveUtil.createEPMove(Long.numberOfTrailingZeros(piece), cb.epIndex));
			piece &= piece - 1;
		}
	}

	private void addPromotionMove(final int fromIndex, final int toIndex) {
		addMove(MoveUtil.createPromotionMove(MoveUtil.TYPE_PROMOTION_Q, fromIndex, toIndex));
		addMove(MoveUtil.createPromotionMove(MoveUtil.TYPE_PROMOTION_N, fromIndex, toIndex));
		if (EngineConstants.GENERATE_BR_PROMOTIONS) {
			addMove(MoveUtil.createPromotionMove(MoveUtil.TYPE_PROMOTION_B, fromIndex, toIndex));
			addMove(MoveUtil.createPromotionMove(MoveUtil.TYPE_PROMOTION_R, fromIndex, toIndex));
		}
	}

	private void addPromotionAttacks(long moves, final int fromIndex, final int[] pieceIndexes) {
		while (moves != 0) {
			final int toIndex = Long.numberOfTrailingZeros(moves);
			addMove(MoveUtil.createPromotionAttack(MoveUtil.TYPE_PROMOTION_Q, fromIndex, toIndex, pieceIndexes[toIndex]));
			addMove(MoveUtil.createPromotionAttack(MoveUtil.TYPE_PROMOTION_N, fromIndex, toIndex, pieceIndexes[toIndex]));
			if (EngineConstants.GENERATE_BR_PROMOTIONS) {
				addMove(MoveUtil.createPromotionAttack(MoveUtil.TYPE_PROMOTION_B, fromIndex, toIndex, pieceIndexes[toIndex]));
				addMove(MoveUtil.createPromotionAttack(MoveUtil.TYPE_PROMOTION_R, fromIndex, toIndex, pieceIndexes[toIndex]));
			}
			moves &= moves - 1;
		}
	}
	
	
	static interface IBetaCutoffMoves {
		
		void addMove(int move);
		
		int getBest1();
		
		int getBest2();
		
		void clear();
	}
	
	
	private static final class BetaCutoffMoves_Counts implements IBetaCutoffMoves {
		
		
		private int[][] moves_piece_to;
		private long[][] counts;

		private int best_move1;
		private int best_move2;
		private long max_count;
		
		
		private BetaCutoffMoves_Counts() {
			
			clear();
		}
		
		
		public void addMove(int move) {
			
			int piece = MoveUtil.getSourcePieceIndex(move);
			int to = MoveUtil.getToIndex(move);
			
			moves_piece_to[piece][to] = move;
			counts[piece][to]++;
			
			if (counts[piece][to] > max_count) {
				
				max_count = counts[piece][to];
				best_move2 = best_move1;
				best_move1 = move;
			}
		}
		
		
		public int getBest1() {
			
			return best_move1;
		}
		
		
		public int getBest2() {
			
			return best_move2;
		}


		@Override
		public void clear() {
			
			//Allow GC to free up the used memory
			moves_piece_to = null;
			counts = null;
			
			moves_piece_to 	= new int[7][64];
			counts 			= new long[7][64];
			
			best_move1 = 0;
			best_move2 = 0;
			max_count = 0;
		}
	}

	
	private static final class BetaCutoffMoves_LastIn implements IBetaCutoffMoves {
		
		
		private int best_move1;
		private int best_move2;
		
		
		private BetaCutoffMoves_LastIn() {
			
		}
		
		
		public void addMove(int move) {
			
			if (best_move1 != move) {
				
				best_move2 = best_move1;
				
				best_move1 = move;
			}
		}
		
		
		public int getBest1() {
			
			return best_move1;
		}
		
		
		public int getBest2() {
			
			return best_move2;
		}
		
		
		@Override
		public void clear() {
			
			best_move1 = 0;
			best_move2 = 0;
		}
	}
}
