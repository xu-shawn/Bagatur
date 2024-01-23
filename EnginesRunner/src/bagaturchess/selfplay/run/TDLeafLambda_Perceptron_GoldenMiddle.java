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
package bagaturchess.selfplay.run;


import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import bagaturchess.bitboard.api.BoardUtils;
import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.impl.Constants;
import bagaturchess.engines.cfg.base.RootSearchConfig_BaseImpl_1Core;
import bagaturchess.engines.cfg.base.UCIConfig_BaseImpl;
import bagaturchess.learning.goldmiddle.impl4.filler.Bagatur_V20_FeaturesConfigurationImpl;
import bagaturchess.learning.impl.features.baseimpl.Features;
import bagaturchess.learning.impl.features.baseimpl.Features_Splitter;
import bagaturchess.search.api.IRootSearch;
import bagaturchess.search.api.IRootSearchConfig;
import bagaturchess.search.impl.env.SharedData;
import bagaturchess.search.impl.rootsearch.sequential.SequentialSearch_MTD;
import bagaturchess.selfplay.GamesRunner;
import bagaturchess.selfplay.train.Trainer;
import bagaturchess.selfplay.train.Trainer_GOLDENMIDDLE;
import bagaturchess.uci.api.ChannelManager;
import bagaturchess.uci.impl.Channel_Console;


public class TDLeafLambda_Perceptron_GoldenMiddle {
	
	
	public static void main(String[] args) {
		
		try {
			
			PrintStream log = new PrintStream(new FileOutputStream(new File("goldenmiddle.log")));
			
			ChannelManager.setChannel(new Channel_Console(System.in, log, log));
			
			ChannelManager.getChannel().initLogging(new UCIConfig_BaseImpl(new String[] {"bagaturchess.search.impl.uci_adaptor.UCISearchAdaptorImpl_PonderingOpponentMove",
					"bagaturchess.engines.cfg.base.UCISearchAdaptorConfig_BaseImpl",
					"agaturchess.search.impl.rootsearch.sequential.SequentialSearch_MTD",
					"bagaturchess.engines.cfg.base.RootSearchConfig_BaseImpl_1Core",
					"bagaturchess.search.impl.alg.impl1.Search_PVS_NWS",
					"bagaturchess.engines.cfg.base.SearchConfigImpl_AB",
					"bagaturchess.learning.goldmiddle.impl4.cfg.BoardConfigImpl_V20",
					"bagaturchess.learning.goldmiddle.impl4.cfg.EvaluationConfig_V20_GOLDENMIDDLE_Train"
					}));
			
			
			IRootSearchConfig cfg = RootSearchConfig_BaseImpl_1Core.EVALIMPL4_TUNING_GOLDENMIDDEL;
			
			SharedData sharedData = new SharedData(ChannelManager.getChannel(), cfg);
			
			IRootSearch search = new SequentialSearch_MTD(new Object[] {cfg, sharedData});
			//IRootSearch searchMultiPV = new MultiPVRootSearch(cfg, search);
			
			sharedData = search.getSharedData();
			
			
			/*MultiPVMediator multipvMediator = new MultiPVMediator(cfg, search, bitboard, mediator1, go);
			multipvMediator.ready();*/
			
			String filename_NN = Features_Splitter.FEATURES_FILE_NAME;
			String features_class_name = Bagatur_V20_FeaturesConfigurationImpl.class.getName();
			
			if (true) {
				
				Features_Splitter features = Features_Splitter.create(features_class_name);
				Features_Splitter.store(filename_NN, features);
				//Features_Splitter.dump(features);
			
			} else {
				
				Features_Splitter features = Features_Splitter.load(filename_NN, features_class_name);
				Features.toJavaCode(features.getFeatures(1), "_O");
				Features.toJavaCode(features.getFeatures(0), "_E");
				System.exit(0);
			}
			
			IBitBoard bitboard = BoardUtils.createBoard_WithPawnsCache(Constants.INITIAL_BOARD, cfg.getBoardConfig());
			
			Trainer ds_builder = new Trainer_GOLDENMIDDLE(bitboard, filename_NN, cfg.getEvalConfig());
			//Trainer ds_builder = new Trainer_GOLDENMIDDLE(bitboard, filename_NN, Bagatur_V20_SignalFiller.eval_config);
			
			
			GamesRunner player = new GamesRunner(bitboard, search, ds_builder);
			
			player.playGames(1000000);
			
		} catch (Throwable t) {
			
			t.printStackTrace();
		}
	}
}
