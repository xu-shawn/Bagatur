/*
 *  BagaturChess (UCI chess engine and tools)
 *  Copyright (C) 2005 Krasimir I. Topchiyski (k_topchiyski@yahoo.com)
 *  
 *  Open Source project location: http://sourceforge.net/projects/bagaturchess/develop
 *  SVN repository https://bagaturchess.svn.sourceforge.net/svnroot/bagaturchess
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
 *  along with BagaturChess. If not, see <http://www.eclipse.org/legal/epl-v10.html/>.
 *
 */
package bagaturchess.search.impl.alg.impl1;


import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.impl.utils.VarStatistic;
import bagaturchess.bitboard.impl1.BoardImpl;
import bagaturchess.bitboard.impl1.internal.Assert;
import bagaturchess.bitboard.impl1.internal.CheckUtil;
import bagaturchess.bitboard.impl1.internal.ChessBoard;
import bagaturchess.bitboard.impl1.internal.EngineConstants;
import bagaturchess.bitboard.impl1.internal.EvalConstants;
import bagaturchess.bitboard.impl1.internal.MaterialUtil;
import bagaturchess.bitboard.impl1.internal.MoveGenerator;
import bagaturchess.bitboard.impl1.internal.MoveUtil;
import bagaturchess.bitboard.impl1.internal.SEEUtil;
import bagaturchess.egtb.syzygy.SyzygyConstants;
import bagaturchess.egtb.syzygy.SyzygyTBProbing;
import bagaturchess.search.api.IEvaluator;
import bagaturchess.search.api.internal.ISearch;
import bagaturchess.search.api.internal.ISearchInfo;
import bagaturchess.search.api.internal.ISearchMediator;
import bagaturchess.search.api.internal.SearchInterruptedException;

import bagaturchess.search.impl.alg.SearchImpl;
import bagaturchess.search.impl.env.SearchEnv;
import bagaturchess.search.impl.pv.PVManager;
import bagaturchess.search.impl.pv.PVNode;
import bagaturchess.search.impl.tpt.ITTEntry;
import bagaturchess.search.impl.utils.SearchUtils;


public class Search_PVS_NWS extends SearchImpl {
	
	
	private static final int PHASE_TT = 0;
	private static final int PHASE_ATTACKING_GOOD = 1;
	private static final int PHASE_COUNTER = 2;
	private static final int PHASE_KILLER_1 = 3;
	private static final int PHASE_KILLER_2 = 4;
	private static final int PHASE_QUIET = 5;
	private static final int PHASE_ATTACKING_BAD = 6;
	
	private static final int[] STATIC_NULLMOVE_MARGIN = { 0, 60, 130, 210, 300, 400, 510 };
	private static final int[] RAZORING_MARGIN = { 0, 240, 280, 300 };
	private static final int[] FUTILITY_MARGIN = { 0, 80, 170, 270, 380, 500, 630 };
	private static final int[][] LMR_TABLE = new int[64][64];
	static {
		for (int depth = 1; depth < 64; depth++) {
			for (int moveNumber = 1; moveNumber < 64; moveNumber++) {
				//LMR_TABLE[depth][moveNumber] = (int) (0.5f + Math.log(depth) * Math.log(moveNumber * 1.2f) / 2.5f);
				LMR_TABLE[depth][moveNumber] = 1 + (int) Math.ceil(Math.max(1, Math.log(moveNumber) * Math.log(depth) / (double) 2));
			}
		}
	}
	
	private static final int FUTILITY_MARGIN_Q_SEARCH = 200;
	
	
	private long lastSentMinorInfo_timestamp;
	private long lastSentMinorInfo_nodesCount;
	
	private VarStatistic historyStatistics;
	
	List<IMoveGenFragment> moveGenFragments;
	
	
	public Search_PVS_NWS(Object[] args) {
		this(new SearchEnv((IBitBoard) args[0], getOrCreateSearchEnv(args)));
	}
	
	
	public Search_PVS_NWS(SearchEnv _env) {
		super(_env);
	}
	
	
	@Override
	public int getTPTUsagePercent() {
		return (int) env.getTPT().getUsage();
	}
	
	
	public void newSearch() {
		
		super.newSearch();
		
		((BoardImpl) env.getBitboard()).getMoveGenerator().clearHistoryHeuristics();
		
		lastSentMinorInfo_nodesCount = 0;
		lastSentMinorInfo_timestamp = 0;
		
		historyStatistics = new VarStatistic(false);
		
		moveGenFragments = new ArrayList<IMoveGenFragment>();
		
		ChessBoard cb = ((BoardImpl) env.getBitboard()).getChessBoard();
		MoveGenerator mg = ((BoardImpl) env.getBitboard()).getMoveGenerator();
		
		moveGenFragments.add(new MoveGenFragmentImpl_TT(cb, mg, env.getTPT()));
		moveGenFragments.add(new MoveGenFragmentImpl_Attacks_GoodAndEqual(cb, mg));
		moveGenFragments.add(new MoveGenFragmentImpl_Counter(cb, mg));
		moveGenFragments.add(new MoveGenFragmentImpl_Killer1(cb, mg));
		moveGenFragments.add(new MoveGenFragmentImpl_Killer2(cb, mg));
		moveGenFragments.add(new MoveGenFragmentImpl_Quiet(cb, mg));
		moveGenFragments.add(new MoveGenFragmentImpl_Attacks_Bad(cb, mg));
	}
	
	
	@Override
	public int pv_search(ISearchMediator mediator, PVManager pvman,
			ISearchInfo info, int initial_maxdepth, int maxdepth, int depth,
			int alpha_org, int beta, int prevbest, int prevprevbest,
			int[] prevPV, boolean prevNullMove, int evalGain, int rootColour,
			int totalLMReduction, int materialGain, boolean inNullMove,
			int mateMove, boolean useMateDistancePrunning) {
		
		return search(mediator, info, pvman, env.getEval(), ((BoardImpl) env.getBitboard()).getChessBoard(),
				((BoardImpl) env.getBitboard()).getMoveGenerator(), 0, normDepth(maxdepth), alpha_org, beta, true);
	}
	
	
	@Override
	public int nullwin_search(ISearchMediator mediator, PVManager pvman, ISearchInfo info,
			int initial_maxdepth, int maxdepth, int depth, int beta,
			boolean prevNullMove, int prevbest, int prevprevbest, int[] prevPV,
			int rootColour, int totalLMReduction, int materialGain,
			boolean inNullMove, int mateMove, boolean useMateDistancePrunning) {
		
		return search(mediator, info, pvman, env.getEval(), ((BoardImpl) env.getBitboard()).getChessBoard(),
				((BoardImpl) env.getBitboard()).getMoveGenerator(), 0, normDepth(maxdepth), beta - 1, beta, false);		
	}
	
	
	public int search(ISearchMediator mediator, ISearchInfo info,
			PVManager pvman, IEvaluator evaluator, ChessBoard cb, MoveGenerator moveGen,
			final int ply, int depth, int alpha, int beta, boolean isPv) {

		
		if (mediator != null && mediator.getStopper() != null) {
			mediator.getStopper().stopIfNecessary(ply + depth, env.getBitboard().getColourToMove(), alpha, beta);
		}
		
		
		if (info.getSelDepth() < ply) {
			info.setSelDepth(ply);
		}
		
		
		if (ply >= ISearch.MAX_DEPTH) {
			return eval(evaluator, ply, alpha, beta, isPv);
		}
		
		
		PVNode node = pvman.load(ply);
		node.bestmove = 0;
		node.eval = ISearch.MIN;
		node.leaf = true;
		
		
	    // Check if we have an upcoming move which draws by repetition, or
	    // if the opponent had an alternative move earlier to this position.
	    if (/*alpha < EvalConstants.SCORE_DRAW
	        &&*/ ply > 0
	        && isDraw()
	        ) {
			node.eval = EvalConstants.SCORE_DRAW;
			return node.eval;
	    }
		
		
		if (EngineConstants.ASSERT) {
			Assert.isTrue(depth >= 0);
			Assert.isTrue(alpha >= ISearch.MIN && alpha <= ISearch.MAX);
			Assert.isTrue(beta >= ISearch.MIN && beta <= ISearch.MAX);
		}
		
		final int alphaOrig = alpha;
		
		depth += extensions(cb, moveGen, ply);
		
		if (EngineConstants.ENABLE_MATE_DISTANCE_PRUNING) {
			if (ply > 0) {
				alpha = Math.max(alpha, -SearchUtils.getMateVal(ply));
				beta = Math.min(beta, +SearchUtils.getMateVal(ply + 1));
				if (alpha >= beta) {
					return alpha;
				}
			}
		}
		
		
		int ttMove = 0;
		env.getTPT().get(cb.zobristKey, tt_entries_per_ply[ply]);
		if (!tt_entries_per_ply[ply].isEmpty() && cb.isValidMove(tt_entries_per_ply[ply].getBestMove())) {
			
			ttMove = tt_entries_per_ply[ply].getBestMove();
			
			int tpt_depth = tt_entries_per_ply[ply].getDepth();
			
			if (tpt_depth >= depth) {
				if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_EXACT) {
					extractFromTT(ply, node, tt_entries_per_ply[ply], info, isPv);
					return node.eval;
				} else {
					if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_LOWER && tt_entries_per_ply[ply].getEval() >= beta) {
						extractFromTT(ply, node, tt_entries_per_ply[ply], info, isPv);
						return node.eval;
					}
					if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_UPPER && tt_entries_per_ply[ply].getEval() <= alpha) {
						extractFromTT(ply, node, tt_entries_per_ply[ply], info, isPv);
						return node.eval;
					}
				}
			}
		}
		
		
		if (ply > 1
    	    	&& depth >= 7
    			&& SyzygyTBProbing.getSingleton() != null
    			&& SyzygyTBProbing.getSingleton().isAvailable(env.getBitboard().getMaterialState().getPiecesCount())
    			){
			
			if (cb.checkingPieces != 0) {
				if (!env.getBitboard().hasMoveInCheck()) {
					node.bestmove = 0;
					node.eval = -getMateVal(ply);
					node.leaf = true;
					return node.eval;
				}
			} else {
				if (!env.getBitboard().hasMoveInNonCheck()) {
					node.bestmove = 0;
					node.eval = EvalConstants.SCORE_DRAW;
					node.leaf = true;
					return node.eval;
				}
			}
			
			int result = SyzygyTBProbing.getSingleton().probeDTZ(env.getBitboard());
			if (result != -1) {
				int dtz = (result & SyzygyConstants.TB_RESULT_DTZ_MASK) >> SyzygyConstants.TB_RESULT_DTZ_SHIFT;
				int wdl = (result & SyzygyConstants.TB_RESULT_WDL_MASK) >> SyzygyConstants.TB_RESULT_WDL_SHIFT;
				int egtbscore =  SyzygyTBProbing.getSingleton().getWDLScore(wdl, ply);
				if (egtbscore > 0) {
					int distanceToDraw = 100 - env.getBitboard().getDraw50movesRule();
					if (distanceToDraw > dtz) {
						node.bestmove = 0;
						node.eval = 9 * (distanceToDraw - dtz);
						node.leaf = true;
						return node.eval;
					} else {
						node.bestmove = 0;
						node.eval = EvalConstants.SCORE_DRAW;
						node.leaf = true;
						return node.eval;
					}
				} else if (egtbscore == 0) {
					node.bestmove = 0;
					node.eval = EvalConstants.SCORE_DRAW;
					node.leaf = true;
					return node.eval;
				}
			}
        }
		
		
		if (depth == 0) {
			int qeval = qsearch(evaluator, info, cb, moveGen, alpha, beta, ply, isPv);
			node.bestmove = 0;
			node.eval = qeval;
			node.leaf = true;
			return node.eval;
		}
		
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		
		
		int eval = ISearch.MIN;
		if (!isPv && cb.checkingPieces == 0) {
			
			
			eval = eval(evaluator, ply, alphaOrig, beta, isPv);
			
			
			if (EngineConstants.USE_TT_SCORE_AS_EVAL && !tt_entries_per_ply[ply].isEmpty()) {
				if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_EXACT
						|| (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_UPPER && tt_entries_per_ply[ply].getEval() < eval)
						|| (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_LOWER && tt_entries_per_ply[ply].getEval() > eval)
					) {
					eval = tt_entries_per_ply[ply].getEval();
				}
			}
			
			
			if (EngineConstants.ENABLE_STATIC_NULL_MOVE && depth < STATIC_NULLMOVE_MARGIN.length) {
				if (eval - STATIC_NULLMOVE_MARGIN[depth] >= beta) {
					node.bestmove = 0;
					node.eval = eval;
					node.leaf = true;
					return node.eval;
				}
			}
			
			
			//Razoring for all depths based on the eval deviation detected into the root node
			/*int rbeta = alpha - mediator.getTrustWindow_AlphaAspiration();
			if (eval < rbeta) {
				score = calculateBestMove(evaluator, info, cb, moveGen, rbeta, rbeta + 1, ply);
				if (score <= rbeta) {
					node.bestmove = 0;
					node.eval = score;
					node.leaf = true;
					return node.eval;
				}
			}*/
			
			
			if (EngineConstants.ENABLE_RAZORING && depth < RAZORING_MARGIN.length && Math.abs(alpha) < EvalConstants.SCORE_MATE_BOUND) {
				if (eval + RAZORING_MARGIN[depth] < alpha) {
					int score = qsearch(evaluator, info, cb, moveGen, alpha - RAZORING_MARGIN[depth], alpha - RAZORING_MARGIN[depth] + 1, ply, isPv);
					if (score + RAZORING_MARGIN[depth] <= alpha) {
						node.bestmove = 0;
						node.eval = score;
						node.leaf = true;
						return node.eval;
					}
				}
			}
			
			
			if (EngineConstants.ENABLE_NULL_MOVE) {
				if (eval >= beta && MaterialUtil.hasNonPawnPieces(cb.materialKey, cb.colorToMove)) {
					cb.doNullMove();
					final int reduction = Math.max(depth / 2, depth / 4 + 3 + Math.min((eval - beta) / 80, 3));
					int score = depth - reduction <= 0 ? -qsearch(evaluator, info, cb, moveGen, -beta, -beta + 1, ply + 1, isPv)
							: -search(mediator, info, pvman, evaluator, cb, moveGen, ply + 1, depth - reduction, -beta, -beta + 1, false);
					cb.undoNullMove();
					if (score >= beta) {
						node.bestmove = 0;
						node.eval = score;
						node.leaf = true;
						return node.eval;
					}
				}
			}
		}
		
		
		final boolean wasInCheck = cb.checkingPieces != 0;
		
		final int parentMove = ply == 0 ? 0 : moveGen.previous();
		int counterMove = 0;
		int killer1Move = 0;
		int killer2Move = 0;
		int movesPerformed = 0;
		
		moveGen.startPly();
		
		Collections.sort(moveGenFragments);
		//System.out.println(moveGenFragments);
		
		/*for (int i = 0; i < moveGenFragments.size(); i++) {
			IMoveGenFragment fragment = moveGenFragments.get(i);
			fragment.genMoves(parentMove, ply, depth, true);
		}*/
		
		int phase = PHASE_TT;
		while (phase <= PHASE_ATTACKING_BAD) {
			
			switch (phase) {
				case PHASE_TT:
					if (ttMove != 0 && cb.isValidMove(ttMove)) {
						moveGen.addMove(ttMove);
					}
					break;
				case PHASE_ATTACKING_GOOD:
					moveGen.generateAttacks(cb);
					moveGen.setMVVLVAScores(cb);
					moveGen.sort();
					break;
				case PHASE_COUNTER:
					counterMove = moveGen.getCounter(cb.colorToMove, parentMove);
					if (counterMove != 0 && counterMove != ttMove && cb.isValidMove(counterMove)) {
						moveGen.addMove(counterMove);
						break;
					} else {
						phase++;
					}
				case PHASE_KILLER_1:
					killer1Move = moveGen.getKiller1(ply);
					if (killer1Move != 0 && killer1Move != ttMove && killer1Move != counterMove && cb.isValidMove(killer1Move)) {
						moveGen.addMove(killer1Move);
						break;
					} else {
						phase++;
					}
				case PHASE_KILLER_2:
					killer2Move = moveGen.getKiller2(ply);
					if (killer2Move != 0 && killer2Move != ttMove && killer2Move != counterMove && cb.isValidMove(killer2Move)) {
						moveGen.addMove(killer2Move);
						break;
					} else {
						phase++;
					}
				case PHASE_QUIET:
					moveGen.generateMoves(cb);
					moveGen.setHHScores(cb.colorToMove, parentMove);
					moveGen.sort();
					break;
				case PHASE_ATTACKING_BAD:
					moveGen.generateAttacks(cb);
					moveGen.setMVVLVAScores(cb);
					moveGen.sort();
			}
			
			while (moveGen.hasNext()) {
				
				final int move = moveGen.next();
				
				//Build and sent minor info
				if (ply == 0) {
					info.setCurrentMove(move);
					info.setCurrentMoveNumber((movesPerformed + 1));
				}
				
				if (info.getSearchedNodes() >= lastSentMinorInfo_nodesCount + 50000) { //Check time on each 50 000 nodes
					
					long timestamp = System.currentTimeMillis();
					
					if (timestamp >= lastSentMinorInfo_timestamp + 1000)  {//Send info each second
					
						mediator.changedMinor(info);
						
						lastSentMinorInfo_timestamp = timestamp;
					}
					
					lastSentMinorInfo_nodesCount = info.getSearchedNodes();
				}
				
				if (phase == PHASE_ATTACKING_GOOD) {
					if (SEEUtil.getSeeCaptureScore(cb, move) < 0) {
						continue;
					}
				}
				
				if (phase == PHASE_ATTACKING_BAD) {
					if (SEEUtil.getSeeCaptureScore(cb, move) >= 0) {
						continue;
					}
				}
				
				if (phase == PHASE_QUIET) {
					if (move == ttMove || move == killer1Move || move == killer2Move || move == counterMove) {
						continue;
					}
				} else if (phase == PHASE_ATTACKING_GOOD || phase == PHASE_ATTACKING_BAD) {
					if (move == ttMove) {
						continue;
					}
				}
				
				if (!isPv && !wasInCheck && movesPerformed > 0 && !cb.isDiscoveredMove(MoveUtil.getFromIndex(move))) {
					
					if (phase == PHASE_QUIET && moveGen.getScore() <= historyStatistics.getEntropy()) {
						
						if (EngineConstants.ENABLE_LMP && depth <= 4 && movesPerformed >= depth * 3 + 3) {
							continue;
						}
						
						if (EngineConstants.ENABLE_FUTILITY_PRUNING && depth < FUTILITY_MARGIN.length) {
							if (!MoveUtil.isPawnPush78(move)) {
								if (eval == ISearch.MIN) {
									eval = eval(evaluator, ply, alphaOrig, beta, isPv);
								}
								if (eval + FUTILITY_MARGIN[depth] <= alpha) {
									continue;
								}
							}
						}
					} else if (EngineConstants.ENABLE_SEE_PRUNING
							&& depth <= 6
							&& phase == PHASE_ATTACKING_BAD
							&& SEEUtil.getSeeCaptureScore(cb, move) < -20 * depth * depth) {
						continue;
					}
				}
				
				if (!cb.isLegal(move)) {
					continue;
				}
				
				cb.doMove(move);
				movesPerformed++;
				
				if (phase == PHASE_QUIET) {
					historyStatistics.addValue(moveGen.getScore(), moveGen.getScore());
				}
				
				if (EngineConstants.ASSERT) {
					cb.changeSideToMove();
					Assert.isTrue(0 == CheckUtil.getCheckingPieces(cb));
					cb.changeSideToMove();
				}
				
				int reduction = 1;
				if (depth >= 2 && movesPerformed > 1 && MoveUtil.isQuiet(move) && !MoveUtil.isPawnPush78(move)) {
					
					reduction = LMR_TABLE[Math.min(depth, 63)][Math.min(movesPerformed, 63)];
					
					if (moveGen.getScore() > historyStatistics.getEntropy()) {
						reduction -= 1;
						if (moveGen.getScore() > historyStatistics.getEntropy() + historyStatistics.getDisperse()) {
							reduction /= 2;
						}
					}
					
					if (move == killer1Move || move == killer2Move || move == counterMove) {
						reduction -= 1;
					}
					
					if (!isPv) {
						reduction += 1;
					}
					
					reduction = Math.min(depth - 1, Math.max(reduction, 1));
				}
				
				int score = alpha + 1;
				try {
					if (EngineConstants.ENABLE_LMR && reduction != 1) {
						score = -search(mediator, info, pvman, evaluator, cb, moveGen, ply + 1, depth - reduction, -alpha - 1, -alpha, false);
					}
					
					if (EngineConstants.ENABLE_PVS && score > alpha && movesPerformed > 1) {
						score = -search(mediator, info, pvman, evaluator, cb, moveGen, ply + 1, depth - 1, -alpha - 1, -alpha, false);
					}
					
					if (node.leaf || score > node.eval) {
						score = -search(mediator, info, pvman, evaluator, cb, moveGen, ply + 1, depth - 1, -beta, -alpha, isPv);
					}
				} catch(SearchInterruptedException sie) {
					moveGen.endPly();
					throw sie;
				}
				
				cb.undoMove(move);
				
				if (MoveUtil.isQuiet(move)) {
					moveGen.addBFValue(cb.colorToMove, move, parentMove, depth);
				}
				
				if (node.leaf || score > node.eval) {
					
					node.bestmove = move;
					node.eval = score;
					node.leaf = false;
					
					if (ply + 1 < ISearch.MAX_DEPTH) {
						pvman.store(ply + 1, node, pvman.load(ply + 1), true);
					}
					
					alpha = Math.max(alpha, score);
					if (alpha >= beta) {
						
						if (MoveUtil.isQuiet(node.bestmove) && cb.checkingPieces == 0) {
							moveGen.addCounterMove(cb.colorToMove, parentMove, node.bestmove);
							moveGen.addKillerMove(node.bestmove, ply);
							moveGen.addHHValue(cb.colorToMove, node.bestmove, parentMove, depth);
						}

						phase += 10;
						break;
					}
				}
			}
			phase++;
		}
		
		/*if (bestMove != 0) {
			for (int i = 0; i < moveGenFragments.size(); i++) {
				IMoveGenFragment fragment = moveGenFragments.get(i);
				fragment.updateWithBestMove(bestMove, depth);
			}
		}*/
		
		moveGen.endPly();
		
		if (movesPerformed == 0) {
			if (cb.checkingPieces == 0) {
				node.bestmove = 0;
				node.eval = EvalConstants.SCORE_DRAW;
				node.leaf = true;
				return node.eval;
			} else {
				node.bestmove = 0;
				node.eval = -SearchUtils.getMateVal(ply);
				node.leaf = true;
				return node.eval;
			}
		}
		
		if (EngineConstants.ASSERT) {
			Assert.isTrue(node.bestmove != 0);
		}
		
		if (!SearchUtils.isMateVal(node.eval)) {
			env.getTPT().put(cb.zobristKey, depth, node.eval, alphaOrig, beta, node.bestmove);
		}
		
		//validatePV(node, depth, isPv);
		
		return node.eval;
	}


	public int qsearch(IEvaluator evaluator, ISearchInfo info, final ChessBoard cb, final MoveGenerator moveGen, int alpha, final int beta, final int ply, final boolean isPv) {
		
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		if (info.getSelDepth() < ply) {
			info.setSelDepth(ply);
		}
		
		int ttMove = 0;
		env.getTPT().get(cb.zobristKey, tt_entries_per_ply[ply]);
		if (!tt_entries_per_ply[ply].isEmpty()) {
			
			ttMove = tt_entries_per_ply[ply].getBestMove();
			
			if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_EXACT) {
				return tt_entries_per_ply[ply].getEval();
			} else {
				if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_LOWER && tt_entries_per_ply[ply].getEval() >= beta) {
					return tt_entries_per_ply[ply].getEval();
				}
				if (tt_entries_per_ply[ply].getFlag() == ITTEntry.FLAG_UPPER && tt_entries_per_ply[ply].getEval() <= alpha) {
					return tt_entries_per_ply[ply].getEval();
				}
			}
		}
		
		if (cb.checkingPieces != 0) {
			return alpha;
		}
		
		int eval = eval(evaluator, ply, alpha, beta, isPv);
		if (eval >= beta) {
			return eval;
		}
		
		final int alphaOrig = alpha;
		
		alpha = Math.max(alpha, eval);
		
		moveGen.startPly();
		
		int phase = PHASE_TT;
		while (phase <= PHASE_ATTACKING_GOOD) {
			switch (phase) {
				case PHASE_TT:
					if (ttMove != 0 && cb.isValidMove(ttMove)) {
						if (env.getBitboard().getMoveOps().isCaptureOrPromotion(ttMove)) {
							moveGen.addMove(ttMove);
						}
					}
					break;
				case PHASE_ATTACKING_GOOD:
					moveGen.generateAttacks(cb);
					moveGen.setMVVLVAScores(cb);
					moveGen.sort();
					break;
			}
			
			while (moveGen.hasNext()) {
				
				final int move = moveGen.next();
				
				if (MoveUtil.isPromotion(move)) {
					if (MoveUtil.getMoveType(move) != MoveUtil.TYPE_PROMOTION_Q) {
						continue;
					}
				} else if (EngineConstants.ENABLE_Q_FUTILITY_PRUNING
						&& eval + FUTILITY_MARGIN_Q_SEARCH
						+ EvalConstants.MATERIAL[MoveUtil.getAttackedPieceIndex(move)] < alpha) {
					continue;
				}
				
				if (EngineConstants.ENABLE_Q_PRUNE_BAD_CAPTURES
						&& !cb.isDiscoveredMove(MoveUtil.getFromIndex(move))
						&& SEEUtil.getSeeCaptureScore(cb, move) <= 0) {
					continue;
				}
				
				if (!cb.isLegal(move)) {
					continue;
				}
				
				cb.doMove(move);
	
				if (EngineConstants.ASSERT) {
					cb.changeSideToMove();
					Assert.isTrue(0 == CheckUtil.getCheckingPieces(cb));
					cb.changeSideToMove();
				}
				
				final int score = -qsearch(evaluator, info, cb, moveGen, -beta, -alpha, ply + 1, isPv);
				
				cb.undoMove(move);
				
				if (score >= beta) {
					if (!SearchUtils.isMateVal(score)) {
						env.getTPT().put(cb.zobristKey, 0, score, alphaOrig, beta, move);
					}
					moveGen.endPly();
					return score;
				}
				alpha = Math.max(alpha, score);
			}
			
			phase++;
		}
		moveGen.endPly();
		
		return alpha;
	}
	
	
	private int extensions(final ChessBoard cb, final MoveGenerator moveGen, final int ply) {
		if (EngineConstants.ENABLE_CHECK_EXTENSION && cb.checkingPieces != 0) {
			return 1;
		}
		return 0;
	}
	
	
	private int eval(IEvaluator evaluator, final int ply, final int alpha, final int beta, final boolean isPv) {
		int eval = (int) evaluator.fullEval(ply, alpha, beta, 0);
		return eval;
	}
	
	
	private boolean extractFromTT(int ply, PVNode result, ITTEntry entry, ISearchInfo info, boolean isPv) {
		
		if (entry.isEmpty()) {
			throw new IllegalStateException("entry.isEmpty()");
		}
		
		result.leaf = true;
		
		if (ply > 0 && isDraw()) {
			result.eval = EvalConstants.SCORE_DRAW;
			result.bestmove = 0;
			return true;
		}

		if (info.getSelDepth() < ply) {
			info.setSelDepth(ply);
		}
		
		result.eval = entry.getEval();
		result.bestmove = entry.getBestMove();
		
		boolean draw = false;
		
		PVNode childNode = result.child;
		
		if (isPv && childNode != null && ((BoardImpl) env.getBitboard()).getChessBoard().isValidMove(result.bestmove)) {
			
			env.getBitboard().makeMoveForward(result.bestmove);
			
			env.getTPT().get(env.getBitboard().getHashKey(), tt_entries_per_ply[ply + 1]);
			
			if (!tt_entries_per_ply[ply + 1].isEmpty()) {
				draw = extractFromTT(ply + 1, childNode, tt_entries_per_ply[ply + 1], info, isPv);
				if (draw) {
					result.eval = EvalConstants.SCORE_DRAW;
				} else {
					result.leaf = false;
				}
			}
			
			env.getBitboard().makeMoveBackward(result.bestmove);
		}
		
		return draw;
	}
	
	
	private Stack<Integer> stack = new Stack<Integer>();
	
	
	private void validatePV(PVNode node, int expectedDepth, boolean isPv) {
		
		if (node.leaf || node.bestmove == 0) {
			throw new IllegalStateException();
		}
		
		int actualDepth = 0;
		PVNode cur = node;
		while(cur != null && cur.bestmove != 0) {
			
			actualDepth++;
			
			if (env.getBitboard().isPossible(cur.bestmove)) {
				env.getBitboard().makeMoveForward(cur.bestmove);
				stack.push(cur.bestmove);
			} else {
				throw new IllegalStateException("not valid move " + env.getBitboard().getMoveOps().moveToString(cur.bestmove));
			}
			
			if (cur.leaf) {
				break;
			}
			
			cur = cur.child;
		}
		
		if (actualDepth < expectedDepth) {
			if (isPv) {
				if (!isDraw()) {
					if (env.getBitboard().isInCheck()) {
						if (env.getBitboard().hasMoveInCheck()) {
							//throw new IllegalStateException("actualDepth=" + actualDepth + ", expectedDepth=" + expectedDepth);
							System.out.println("NOT ok in check");	
						}
					} else {
						if (env.getBitboard().hasMoveInNonCheck()) {
							//throw new IllegalStateException("actualDepth=" + actualDepth + ", expectedDepth=" + expectedDepth);
							System.out.println("NOT ok in noncheck");
						}
					}
				}
			}
		}
		
		try {
			Integer move;
			while ((move = stack.pop()) != null) {
				env.getBitboard().makeMoveBackward(move);
			}
		} catch(EmptyStackException ese) {
			//Do nothing
		}
	}
}
