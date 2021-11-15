/**
 * FrankWalter - a java chess engine
 * Copyright 2019 Laurens Winkelhagen (ljgw@users.noreply.github.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package bagaturchess.egtb.syzygy;


import bagaturchess.bitboard.api.BoardUtils;
import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.impl.Constants;
import bagaturchess.bitboard.impl.movegen.MoveInt;
import bagaturchess.bitboard.impl.movelist.BaseMoveList;
import bagaturchess.bitboard.impl.movelist.IMoveList;
import bagaturchess.egtb.syzygy.OnlineSyzygy.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.winkelhagen.chess.syzygy.SyzygyBridge;


/**
 * converter class to fit the FrankWalter board representation on the SyzygyBridge
 */
public class SyzygyTBProbing {
	
	
	private static int MAX_PIECES_COUNT = 7;//Including both kings
	
	
	private static boolean loadingInitiated;
	
	private static SyzygyTBProbing instance;
	
	private IMoveList temp_moves_list;
	
	
    public static final SyzygyTBProbing getSingleton() {
    	
    	if (instance == null && !loadingInitiated) {
    		
    		loadingInitiated = true;
    		
    		instance = new SyzygyTBProbing();
    		
    		if (!instance.loadNativeLibrary()) {
    			
    			instance = null;
    		}
    	}
    	
    	return instance;
    }
    
    
    private SyzygyTBProbing() {
    	
    	loadingInitiated = false;
    	
    	temp_moves_list = new BaseMoveList();
    }
    
    
	private boolean loadNativeLibrary() {
		
		return SyzygyBridge.loadNativeLibrary();
	}
	
	
    public final void load(String path) {
    	
    	SyzygyBridge.load(path);
    }
    
    
    /**
     * wrapper for {@link com.winkelhagen.chess.syzygy.SyzygyBridge#isAvailable(int)}
     * @param piecesLeft the number of pieces left on the board
     * @return true iff there is a Syzygy result to be expected, given the number of pieces currently on the board
     */
    public boolean isAvailable(int piecesLeft) {
    	
    	if (piecesLeft > MAX_PIECES_COUNT) {
    		
    		return false;
    	}
    	
        return SyzygyBridge.isAvailable(piecesLeft);
    }
    
    
    /**
     * probes the Syzygy TableBases for a WinDrawLoss result
     * @param board the FrankWalter board representation
     * @return a WDL result (see {@link #getWDLScore(int, int)})
     */
    public int probeWDL(IBitBoard board){
    	
    	
    	if (board.getMaterialState().getPiecesCount() > MAX_PIECES_COUNT) {
    		
    		return -1;
    	}
    	
    	
    	
        if (board.hasRightsToKingCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToKingCastle(Constants.COLOUR_BLACK)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_BLACK)) {
        	
            return -1;
        }
        
        
        return SyzygyBridge.probeSyzygyWDL(
        		convertBB(board.getFiguresBitboardByColour(Constants.COLOUR_WHITE)),
        		convertBB(board.getFiguresBitboardByColour(Constants.COLOUR_BLACK)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_KING)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_KING)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_QUEEN)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_QUEEN)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_ROOK)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_ROOK)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_BISHOP)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_BISHOP)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_KNIGHT)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_KNIGHT)),
        		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_PAWN)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_PAWN)),
                0,//board.getEpSquare()==-1?0:board.getEpSquare(),
                board.getColourToMove() == Constants.COLOUR_WHITE
        );
    }
    

	/**
     * probes the Syzygy TableBases for a DistanceToZero result.
     * If castling is still allowed, no accurate DTZ can be given
     * @param board the FrankWalter board representation
     * @return a WDL result (see {@link #toXBoardScore(int)} and {@link #toMove(int)})
     */
    public int probeDTZ(IBitBoard board) {
    	
    	
    	if (board.getMaterialState().getPiecesCount() > MAX_PIECES_COUNT) {
    		
    		return -1;
    	}
    	
    	
        if (board.hasRightsToKingCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToKingCastle(Constants.COLOUR_BLACK)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_BLACK)) {
        	
            return -1;
        }
       
        
        int score = SyzygyBridge.probeSyzygyDTZ(
        		
    		convertBB(board.getFiguresBitboardByColour(Constants.COLOUR_WHITE)),
    		convertBB(board.getFiguresBitboardByColour(Constants.COLOUR_BLACK)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_KING)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_KING)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_QUEEN)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_QUEEN)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_ROOK)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_ROOK)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_BISHOP)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_BISHOP)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_KNIGHT)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_KNIGHT)),
    		convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_WHITE, Constants.TYPE_PAWN)) | convertBB(board.getFiguresBitboardByColourAndType(Constants.COLOUR_BLACK, Constants.TYPE_PAWN)),
            board.getDraw50movesRule(),
            0,//board.getEpSquare()==-1?0:board.getEpSquare(),
            board.getColourToMove() == Constants.COLOUR_WHITE
        );
	        
        return score;
    }


	public synchronized void probeMove(IBitBoard board, long[] out) {
    	
    	
    	out[0] = -1;
		out[1] = -1;
		
		//Check pieces count
		if (board.getMaterialState().getPiecesCount() > MAX_PIECES_COUNT) {
			
			return;
		}
		
		
		//Check castling rights
        if (board.hasRightsToKingCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_WHITE)
        		|| board.hasRightsToKingCastle(Constants.COLOUR_BLACK)
        		|| board.hasRightsToQueenCastle(Constants.COLOUR_BLACK)) {
			
			return;
        }
		
        
        temp_moves_list.clear();
        
		board.genAllMoves(temp_moves_list);
		
		List<MoveWDLPair> moves = new ArrayList<MoveWDLPair>();
		
		int cur_move;
		
		while ((cur_move = temp_moves_list.next()) != 0) {
			
			
			board.makeMoveForward(cur_move);
			
			
			int probe_result = SyzygyTBProbing.getSingleton().probeDTZ(board);
			
			int dtz = (probe_result & SyzygyConstants.TB_RESULT_DTZ_MASK) >> SyzygyConstants.TB_RESULT_DTZ_SHIFT;
			int wdl = (probe_result & SyzygyConstants.TB_RESULT_WDL_MASK) >> SyzygyConstants.TB_RESULT_WDL_SHIFT;
			
			//int wdl = SyzygyTBProbing.getSingleton().probeWDL(board);
			//wdl = (wdl & SyzygyConstants.TB_RESULT_WDL_MASK) >> SyzygyConstants.TB_RESULT_WDL_SHIFT;
			
			//System.out.println(board.getMoveOps().moveToString(cur_move) + ", dtz=" + dtz + ", wdl=" + wdl);
			
			
			//From opponent perspective the win is loss
			if (wdl == SyzygyConstants.TB_LOSS) {
				
				moves.add(new MoveWDLPair(wdl, dtz, cur_move));
			}
			
			
			board.makeMoveBackward(cur_move);
		}
		
		
		if (moves.size() > 0) {
			
			Collections.sort(moves);
			
			for (int i = 0; i < moves.size(); i++){
				System.out.println(board.getMoveOps().moveToString((int) moves.get(i).move) + ", dtz=" + moves.get(i).dtz + ", wdl=" + moves.get(i).wdl);
			}
			
			MoveWDLPair best = moves.get(0);
			
			out[0] = best.dtz;
			
			out[1] = best.move;
		}
	}
	
	
	
    public int toMove(int result){
        int from = (result & SyzygyConstants.TB_RESULT_FROM_MASK) >> SyzygyConstants.TB_RESULT_FROM_SHIFT;
        int to = (result & SyzygyConstants.TB_RESULT_TO_MASK) >> SyzygyConstants.TB_RESULT_TO_SHIFT;
        int promotes = (result & SyzygyConstants.TB_RESULT_PROMOTES_MASK) >> SyzygyConstants.TB_RESULT_PROMOTES_SHIFT;
        return getMove(from, to, promotes);
    }
    
    
    public int getMove(int fromSquare, int toSquare, int promotes) {
        return fromSquare | (toSquare <<6) | (promotes << 12);
    }
    
    
    /**
     * returns the score to use inside the main search, based on the WDL result of a TableBase query and the search depth
     * @param wdl the WinDrawLoss result from the probe
     * @param depth the depth of the current search
     * @return the score associated with this position
     */
    public int getWDLScore(int wdl, int depth) {
    	
        switch (wdl) {
        
            case SyzygyConstants.TB_LOSS:
                return -28000 + depth;
                
            case SyzygyConstants.TB_BLESSED_LOSS:
            	return 0;
                //return -27000 + depth;
                
            case SyzygyConstants.TB_DRAW:
                return 0;
                
            case SyzygyConstants.TB_CURSED_WIN:
            	return 0;
                //return 27000 - depth;
                
            case SyzygyConstants.TB_WIN:
                return 28000 - depth;
                
            default:
            	throw new IllegalStateException("wdl=" + wdl);
                //return 0;
        }
    }
    
    
	private static long convertBB(long pieces) {
		return pieces;
	}
	
	
	private class MoveWDLPair implements Comparable {
		
		
		long wdl;
		
		long dtz;
		
		long move;
		
		
		private MoveWDLPair(long _wdl, long _dtz, long _move) {
			
			if (_wdl != SyzygyConstants.TB_LOSS) {
				
				throw new IllegalStateException("Use this method only in the root search");
			}
			
			wdl = _wdl;
			
			dtz = _dtz;
			
			move = _move;
		}
		
		
		/**
		 * Compares this object with the specified object for order.  Returns a
		 * negative integer, zero, or a positive integer as this object is less
	 	 * than, equal to, or greater than the specified object.
		 */
		public int compareTo(Object other) {
			
			
			if (!(other instanceof MoveWDLPair)) {
				
				return -1;
			}
					
			
			long diff =  dtz - ((MoveWDLPair)other).dtz;
			
			if (diff == 0) {
				
				return -1;//equals
			}
			
			return (int) diff;
		}
		
		
		@Override
		public boolean equals(Object o) {
			
			if (o instanceof MoveWDLPair) {
				
				MoveWDLPair other = (MoveWDLPair) o;
				
				return dtz == other.dtz && move == other.move;
			}
			
			return false;
		}
		
		
		@Override
		public int hashCode() {
			
			return (int) (100 * (dtz + 1) + move);
		}
		
		
	    /*@Override
	    public String toString() {
	    	
	    	return super.toString();
	    }*/
	}
	
	
	public static void main(String[] args) {
		
		IBitBoard board  = BoardUtils.createBoard_WithPawnsCache("4k3/8/8/8/8/8/3R4/4K3 w - - 0 1");
		//IBitBoard board = BoardUtils.createBoard_WithPawnsCache(Constants.INITIAL_BOARD);
		
		//String response = getDTZandDTM_BlockingOnSocketConnection(board, result);
		//System.out.println("dtz=" + result[0]);
		//System.out.println("wdl=" + result[1]);
		
		long[] result = new long[2];
		
		SyzygyTBProbing.getSingleton().load("C:\\Users\\i027638\\OneDrive - SAP SE\\DATA\\OWN\\chess\\EGTB\\syzygy");
		
		SyzygyTBProbing.getSingleton().probeMove(board, result);
		
		//System.out.println("dtz=" + result[0]);
		//System.out.println("winner=" + result[1]);
		
		System.out.println("result[0]=" + result[0] + ", result[1]=" + result[1]);
		
		if (result[1] != 0) {
			
			System.out.println("BESTMOVE: " + board.getMoveOps().moveToString((int) result[1]));
		}
	}
}