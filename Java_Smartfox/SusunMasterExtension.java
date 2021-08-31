package mbs.diamond.SusunMasterExtension;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import com.smartfoxserver.bitswarm.sessions.ISession;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.db.DBConfig;
import com.smartfoxserver.v2.db.IDBManager;
import com.smartfoxserver.v2.db.SFSDBManager;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSArray;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSArray;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.ExtensionLogLevel;
import com.smartfoxserver.v2.extensions.SFSExtension;

import mbs.diamond.SusunMasterExtension.Data.SusunRoomInfo;
import mbs.diamond.SusunMasterExtension.Data.SusunUserData;
import mbs.diamond.capsasusun.ConstantClass;
import mbs.diamond.capsasusun.DBManager;
import mbs.diamond.capsasusun.ErrorClass;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class SusunMasterExtension extends SFSExtension
{
	// Server Info Variables
	private final String version = "2.0.5";
	private final int _nettyPort = 4340;
	private boolean _showDetailLog = false;
	
	// Netty Variables
	private ServerBootstrap _serverBootstrap = null;
	
	// Server Variables
	public String CustServiceStage = "OFF";
	public boolean _leagueStatus = true;
	
	// Beginner : 500, 1K, 3K ,5K -> 0-3
	// Expert   : 10K, 20K, 50K, 100k, 250K -> 4-8
	// Master   : 1M, 5M, 10M, 25M -> 9-12
	public final long[] baseBet = {500, 1000, 3000, 5000, 10000, 20000, 50000, 100000, 250000, 1000000, 5000000, 10000000, 25000000};
	public final long[] minimumHandCoin = {5000, 30000, 90000, 125000, 250000, 500000, 1250000, 2500000, 5000000, 20000000, 100000000, 20000000, 500000000};
	public final long[] recomendedHandCoin = {5000, 250000, 500000, 1500000, 2500000, 5000000, 10000000, 25000000, 50000000, 125000000, 500000000, 1000000000L, 2000000000L, 2000000000L};

	private ArrayList<Long> _expTable = new ArrayList<Long>();
	
	public ArrayList<String> allowedGameServer = new ArrayList<String>();
	private ConcurrentHashMap<String, Channel> _gameServer = new ConcurrentHashMap<String, Channel>();
	private ConcurrentHashMap<String, Integer> _gameServerLimit = new ConcurrentHashMap<String, Integer>();
	private ConcurrentHashMap<String, Integer> _gameServerTotalUser = new ConcurrentHashMap<String, Integer>();
	private ConcurrentHashMap<String, Integer> _gameServerTotalRoom = new ConcurrentHashMap<String, Integer>();
	
	private ConcurrentHashMap<Integer, Integer> _userWaitingRoomInfo = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, ISFSObject> createRoomList = new ConcurrentHashMap<Integer, ISFSObject>();
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>> publicRoom = new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>>();
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>> vipRoom = new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>>();
	// Tournament Round start from 1
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>> tournamentRoom = new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SusunRoomInfo>>();
	
	// Find Room Update Information
	public ConcurrentHashMap<Integer, ArrayList<String>> FindRoomUserInfo = new ConcurrentHashMap<Integer, ArrayList<String>>();
	public ArrayList<ISFSObject> FindRoomUpdateInfo = new ArrayList<ISFSObject>();
	public ArrayList<String> PendingFindRoomUser = new ArrayList<String>();
	public int pendingFindRoomKey = -1;
	
	public ConcurrentHashMap<Integer, SusunUserData> userData = new ConcurrentHashMap<Integer, SusunUserData>();
	
	// Room handler
	private ScheduledFuture<?> createLoopHandle;
	private ScheduledFuture<?> roomCheckupHandler;
	private ScheduledFuture<?> updateJackpotHandler;

	// Log Database
	private IDBManager _logDbManager = null;
	private DBConfig _logConfig = null;
	
	// Main Database
	private IDBManager _mainDbManager = null;
	private DBConfig _mainConfig = null;

	// Portal Database
	private IDBManager _portalDbManager = null;
	private DBConfig _portalConfig = null;
	
	JedisPool jedisPool = null;
	
	// Event System
	
	// Push Event
	private boolean _isLoadPushEvent = false;
	private ScheduledFuture<?> _pushEventSchedule;
	private ScheduledFuture<?> _loginPushSchedule;
	//  0 -> No Event
	//  1 -> Waiting Event To Start
	//  2 -> Event is Running
	private int _pushEventState = 0;
	private int _pushEventCurrent = -1;
	private ArrayList<ISFSObject> _pushEventData = new ArrayList<ISFSObject>();
	private ArrayList<Integer> _listPushGiftOnline;
	private ArrayList<Integer> _listPushGiftLogin = new ArrayList<Integer>();
	private Set<Integer> _listPushDoneGift = new HashSet<Integer>();
	
	// Hot Time Event
	private boolean _isLoadHotTimeEvent = false;
	private ScheduledFuture<?> _hotTimeEventSchedule;
	// 0 -> No Event
	// 1 -> Waiting Event To Start
	// 2 -> Event is Running
	private int _hotTimeEventState = 0;
	private int _hotTimeEventCurrent = -1;
	private ArrayList<ISFSObject> _hotTimeEventData = new ArrayList<ISFSObject>();
	
	// Async HTPP
	AsyncHttp _async_http;
	
	// Jackpot
	private AtomicLong _jackpot = new AtomicLong(0);
	FileOutputStream _outputstream = null;
	
	// hot time add on
	private int _hotTimeMinMulti = 2;
	private int _hotTimeMaxMulti = 20;
	
	/*
	 * Extension
	 */
	
	@Override
	public void init()
	{
		trace(ExtensionLogLevel.INFO, String.format("SusunMasterExtension v%s Initialize.", version));
		// Event Listener
		addEventHandler(SFSEventType.SERVER_READY, ServerReadyEventHandler.class);
		addEventHandler(SFSEventType.USER_LOGIN, UserLoginEventHandler.class);
		addEventHandler(SFSEventType.USER_JOIN_ZONE, UserJoinZoneEventHandler.class);
		addEventHandler(SFSEventType.USER_DISCONNECT, UserLogoutEventHandler.class);
		addEventHandler(SFSEventType.USER_LOGOUT, UserLogoutEventHandler.class);
		// Request Handler
		addRequestHandler(ConstantClass.KEEP_ALIVE, UserKeepAliveHandler.class);
		addRequestHandler(ConstantClass.SEARCH_USER, UserSearhNicknameHandler.class);
		// Friends Handler
		addRequestHandler(ConstantClass.LIST_MY_FRIENDS, UserListFriendsHandler.class);
		addRequestHandler(ConstantClass.ADD_FRIEND_REQUEST, UserAddFriendHandler.class);
		addRequestHandler(ConstantClass.RESULT_FRIEND_REQUEST, UserFriendRequesResultHandler.class);
		addRequestHandler(ConstantClass.ACCEPT_ALL_FRIEND_REQUEST, UserAcceptAllFriendRequestHandler.class);
		addRequestHandler(ConstantClass.DELETE_FRIEND, UserDeleteFriendHandler.class);
		// Normal Rooms Handler
		addRequestHandler(ConstantClass.CREATE_ROOM, UserCreateRoomHandler.class);
		addRequestHandler(ConstantClass.PLAY_NOW, UserPlayNowHandler.class);
		// Tournament Room Handler
		addRequestHandler(ConstantClass.JOIN_TOURNAMENT, UserJoinTournamentHandler.class);
		// Common Room Handler
		addRequestHandler(ConstantClass.GET_ALL_ROOM_PARAMS, AllRoomParameterHandler.class);
		addRequestHandler(ConstantClass.FIND_ROOM, userFindRoomHandler.class);
		addRequestHandler(ConstantClass.CHECK_IS_IN_GAME_ROOM, UserCheckCurrentStatusHandler.class);
		addRequestHandler(ConstantClass.INVITE_GET_USERS, UserInviteGameGetListHandler.class);
		addRequestHandler(ConstantClass.INVITE_TO_GAME, UserInviteToGameHandler.class);
		addRequestHandler(ConstantClass.EXIT_FIND_ROOM, UserLeaveFindRoomHandler.class);
		// Hot Time Event
		addRequestHandler(ConstantClass.GET_HOT_TIME_EVENT, UserGetHotTimeHandler.class);
		// CS VIP Client
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_CLIENT_HISTORY, CustSvcClientHistoryHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_CLIENT_CHAT, CustSvcClientChatHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_CLIENT_READ_CHECK, CustSvcClientReadCheckHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_CLIENT_STATUS, CustSvcClientServiceStatusHandler.class);
		// CS VIP Operator
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_CASE_LIST, CustSvcOperatorCaseList.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_SINGLE_HISTORY, CustSvcOperatorSingleHistoryHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_CHAT, CustSvcOperatorChatHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_READ_CHECK, CustSvcOperatorReadCheckHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_SEARCH_NICKNAME, CustSvcOperatorSearchNicknameHandle.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_OPERATOR_SEARCH_ID, CustSvcOperatorSearchIDHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_STAGE_STATUS, CustSvcStageStatusHandler.class);
		addRequestHandler(ConstantClass.CUSTOMER_SERVICE_STAGE_QUERY, CustSvcStageQueryHandler.class);
		//Jackpot
		addRequestHandler(ConstantClass.GET_JACKPOT, UserGetJackpotHandler.class);
		
		
		
		LoadConfig();
		DBManager.getInstance().SetLogger(this.getLogger());
		DBManager.getInstance().setMainDBManager(_mainDbManager);
		DBManager.getInstance().setLogDBManager(_logDbManager);
		DBManager.getInstance().setPortalDbManager(_portalDbManager);
		DBManager.getInstance().setRanklDbManager(jedisPool);
		
		_async_http = new AsyncHttp(this);
	}
	
	@Override
	public void destroy() 
	{
		updateJackpotHandler.cancel(true);
		createLoopHandle.cancel(true);
		roomCheckupHandler.cancel(true);
		if (_pushEventState > 0)
		{
			if (_pushEventSchedule != null)
			{
				_pushEventSchedule.cancel(true);
			}
			if (_loginPushSchedule != null)
			{
				_loginPushSchedule.cancel(true);
			}
		}
		if (_hotTimeEventState > 0)
		{
			if (_hotTimeEventSchedule != null)
			{
				_hotTimeEventSchedule = null;
			}
		}

		trace(ExtensionLogLevel.INFO, "CapsaSusun MasterExtension Destroyed.");
		super.destroy();
	}
	
	@Override
    public Object handleInternalMessage(String cmdName, Object params)
    {
        Object result = null;
        try
		{
        	switch(cmdName)
			{
				case "ServerReady":
					ServerReady();
					break;
				case "NettyMessageReceived":
					NettyMessageReceived((MessageEvent)params);
					break;
				case "NettyChannelDisconnected":
					RemoveGameServer((Channel)params);
					break;
				// Servlet
        		case "HTTP_SERVER_INFO":
        			result = GetServerInfoJson();
        			break;
        		case "HTTP_IS_USER_ONLINE":
        			result = IsUserOnline(params.toString());
        			break;
        		case "HTTP_IS_USER_PLAYING_GAME":
        			result = IsUserPlayingGame(params.toString());
        			break;
        		case "HTTP_LIST_ONLINE_USERS":
        			result = ListOnlineUsersJson();
        			break;
        		case "HTTP_LOAD_PUSH_EVENT":
        			LoadPushEventData();
        			result = "Push Event will be loaded.";
        			break;
        		case "HTTP_LIST_PUSH_EVENT":
            		result = ListPushEventJson();
            		break;	
        		case "HTTP_DELETE_PUSH_EVENT":
	        			result = DeletePushEvent((ISFSObject)params);
	            	break;
        		case "HTTP_LOAD_HOT_TIME_EVENT":
        			LoadHotTimeEventData();
        			result = "Hot Time Event will be loaded.";
        			break;
        		case "HTTP_LIST_HOT_TIME_EVENT":
            		result = ListHotTimeEventJson();
            		break;	
        		case "HTTP_DELETE_HOT_TIME_EVENT":
	        			result = DeleteHotTimeEvent((ISFSObject)params);
	            	break;
        		case "HTTP_SEND_ADMIN_MESSAGE":
        			SendAdminMessage(params.toString());
        			result = "Admin Message Sent.";
        			break;
        		case "HTTP_SET_LEAGUE_STATUS":
        			result = SetLeagueStatus(params.toString());
        			break;
        			
        		case "HTTP_SHOW_JACKPOT":
        			result = "Jackpot :" + _jackpot;
        			break;
        		case "HTTP_UPDATE_JACKPOT":
        			result = UpdateJackpot(params.toString());
        			break;
   
        		case "HTTP_SHOW_LOG_STATUS":
        			result = String.format("Show Detail Log : %s.", _showDetailLog);
        			break;
        		case "HTTP_SET_LOG_STATUS":
        			result = UpdateDetailLogStatus(params.toString());
        			break;
        		default:
        			trace(ExtensionLogLevel.ERROR, String.format("handleInternalMessage : Unknown Command : %s.", cmdName));
        			break;
			}
		}
		catch(Exception e)
		{
			result = null;
			trace(ExtensionLogLevel.ERROR, String.format("handleInternalMessage Command : %s, Params : %s, Error : %s.", cmdName, params.toString(), ErrorClass.StackTraceToString(e)));
		}
        return result;
    }
	
	private void LoadConfig()
	{
		try
		{
			Properties properties = this.getConfigProperties();
			// Load GameServer Allowed List
			String stringAllowed = properties.getProperty("AllowedGameServerList");
			allowedGameServer.addAll(Arrays.asList(stringAllowed.split(",")));
			// Setup Main Database Manager
			{
		    	String jdbcURL 					= properties.getProperty("main.jdbc.url");
		    	String jdbcDriver 				= properties.getProperty("main.jdbc.driver");
		    	String jdbcUser 				= properties.getProperty("main.jdbc.user");
		    	String jdbcPWD 					= properties.getProperty("main.jdbc.password");
		    	String maxActiveConnections 	= properties.getProperty("main.jdbc.maxActiveConnections");
		    	String maxIdleConnections 		= properties.getProperty("main.jdbc.maxIdleConnections");

		    	_mainConfig = new DBConfig();
		    	_mainConfig.active = true;
		    	_mainConfig.driverName = jdbcDriver;
				
		    	_mainConfig.connectionString = jdbcURL;
		    	_mainConfig.userName = jdbcUser;
		    	_mainConfig.password = jdbcPWD;
				
		    	_mainConfig.testSql = "SELECT 1";
		    	_mainConfig.maxActiveConnections = Integer.valueOf(maxActiveConnections);
		    	_mainConfig.maxIdleConnections = Integer.valueOf(maxIdleConnections);
		    	_mainConfig.exhaustedPoolAction = "GROW";
		    	_mainConfig.blockTime = 3000;
		    	_mainDbManager = new SFSDBManager(_mainConfig);
		    	_mainDbManager.init(null);
			}
			// Setup Log Database Manager
			{
		    	String jdbcURL 					= properties.getProperty("log.jdbc.url");
		    	String jdbcDriver 				= properties.getProperty("log.jdbc.driver");
		    	String jdbcUser 				= properties.getProperty("log.jdbc.user");
		    	String jdbcPWD 					= properties.getProperty("log.jdbc.password");
		    	String maxActiveConnections 	= properties.getProperty("log.jdbc.maxActiveConnections");
		    	String maxIdleConnections 		= properties.getProperty("log.jdbc.maxIdleConnections");

				_logConfig = new DBConfig();
				_logConfig.active = true;
				_logConfig.driverName = jdbcDriver;
				
				_logConfig.connectionString = jdbcURL;
				_logConfig.userName = jdbcUser;
				_logConfig.password = jdbcPWD;
				
				_logConfig.testSql = "SELECT 1";
				_logConfig.maxActiveConnections = Integer.valueOf(maxActiveConnections);
				_logConfig.maxIdleConnections = Integer.valueOf(maxIdleConnections);
				_logConfig.exhaustedPoolAction = "GROW";
				_logConfig.blockTime = 3000;
				_logDbManager = new SFSDBManager(_logConfig);
				_logDbManager.init(null);
			}
			// Setup Portal Dabatase Manager
			{
		    	String jdbcURL 					= properties.getProperty("portal.jdbc.url");
		    	String jdbcDriver 				= properties.getProperty("portal.jdbc.driver");
		    	String jdbcUser 				= properties.getProperty("portal.jdbc.user");
		    	String jdbcPWD 					= properties.getProperty("portal.jdbc.password");
		    	String maxActiveConnections 	= properties.getProperty("portal.jdbc.maxActiveConnections");
		    	String maxIdleConnections 		= properties.getProperty("portal.jdbc.maxIdleConnections");
		    	
		    	_portalConfig = new DBConfig();
		    	_portalConfig.active = true;
		    	_portalConfig.driverName = jdbcDriver;
				
		    	_portalConfig.connectionString = jdbcURL;
		    	_portalConfig.userName = jdbcUser;
		    	_portalConfig.password = jdbcPWD;
				
		    	_portalConfig.testSql = "SELECT 1";
		    	_portalConfig.maxActiveConnections = Integer.valueOf(maxActiveConnections);
		    	_portalConfig.maxIdleConnections = Integer.valueOf(maxIdleConnections);
		    	_portalConfig.exhaustedPoolAction = "GROW";
		    	_portalConfig.blockTime = 3000;
		    	_portalDbManager = new SFSDBManager(_portalConfig);
				_portalDbManager.init(null);
			}
			// Redis Server
			{
				String rankRedisAddr = properties.getProperty("redisaddr");
				if(rankRedisAddr.equals("10.8.3.17"))
		        {
					jedisPool = new JedisPool(new JedisPoolConfig(), rankRedisAddr,6379,1000);
		        }
		        else
		        {
		        	jedisPool = new JedisPool(new JedisPoolConfig(), rankRedisAddr,6379,1000,"mbadmin#@!123");
		        }
			}
			trace(ExtensionLogLevel.INFO, "CapsaSusun MasterExtension Config Loaded.");
			
			// Jackpot
			
			try
			{
				FileInputStream is = new FileInputStream("jackpot.cfg");
				Properties prop = new Properties();
				prop.load(is);
				_jackpot.set(Long.valueOf(prop.getProperty("jackpot")));
				trace(ExtensionLogLevel.INFO, String.format("Jackpot Loaded, value : %s.", _jackpot));
			}
			catch(FileNotFoundException e)
			{
				_jackpot.set(100000000L);
				trace(ExtensionLogLevel.ERROR, String.format("Load Jackpot Error : %s.", ErrorClass.StackTraceToString(e)));
			}
			catch(SecurityException s)
			{
				_jackpot.set(100000000L);
				trace(ExtensionLogLevel.ERROR, String.format("Load Jackpot Error : %s.", ErrorClass.StackTraceToString(s)));
			}
			catch(Exception a)
			{
				_jackpot.set(100000000L);
				trace(ExtensionLogLevel.ERROR, String.format("Load Jackpot Error : %s.", ErrorClass.StackTraceToString(a)));
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("LoadConfig Error : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public void SendAdminMessage(String message)
	{
		ArrayList<ISession> sessionList = (ArrayList<ISession>) this.getParentZone().getSessionList();
		if (sessionList.size() > 0)
		{
			ISFSObject sendObj = new SFSObject();
			sendObj.putUtfString(ConstantClass.MESSAGE, message);
			this.getApi().sendAdminMessage(null, "broadcast", sendObj, sessionList);
		}
		LogMessage(ExtensionLogLevel.INFO, String.format("Send AdminMessage : %s.", message));
	}
	
	
	
	/*
	 * Event Callback
	 */
	
	public void ServerReady()
	{
		try
		{
			MakeExpTable();
			// Generate Room Placeholder
			int totalBetGrade = baseBet.length;
			for(int i = 0; i < totalBetGrade; i++)
			{
				ConcurrentHashMap<Integer, SusunRoomInfo> tempPublic = new ConcurrentHashMap<Integer, SusunRoomInfo>();
				publicRoom.put(i, tempPublic);
				ConcurrentHashMap<Integer, SusunRoomInfo> tempVip = new ConcurrentHashMap<Integer, SusunRoomInfo>();
				vipRoom.put(i, tempVip);
				ArrayList<String> tempUserList = new ArrayList<String>();
				FindRoomUserInfo.put(i, tempUserList);
			}
			for(int i = 1; i <= 3; i++)
			{
				ConcurrentHashMap<Integer, SusunRoomInfo> tempTournament = new ConcurrentHashMap<Integer, SusunRoomInfo>();
				tournamentRoom.put(i, tempTournament);
			}
			// Start Netty Server
			_serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
			_serverBootstrap.setPipelineFactory(new NettyServerPipelineFactory());
			_serverBootstrap.setOption("child.tcpNoDelay", true);
			_serverBootstrap.setOption("child.keepAlive", true); 
			_serverBootstrap.bind(new InetSocketAddress(_nettyPort));
			trace(ExtensionLogLevel.INFO, "CapsaSusun MasterExtension Netty Server Started.");
				
			SmartFoxServer sfs = SmartFoxServer.getInstance();
			createLoopHandle = sfs.getTaskScheduler().scheduleAtFixedRate(new RoomCreationHandler(), 50, 50, TimeUnit.MILLISECONDS);
			roomCheckupHandler = sfs.getTaskScheduler().scheduleAtFixedRate(new RoomIdleCheckHandler(), 60, 60, TimeUnit.SECONDS);
			updateJackpotHandler = sfs.getTaskScheduler().scheduleAtFixedRate(new JackpotAlert(), 3, 3, TimeUnit.SECONDS);
			
			/*
			// Fake Room for test
			for (int i = 0; i < 300; i++)
			{
				SusunRoomInfo tempRoom = new SusunRoomInfo();
				tempRoom.roomKey = 1000 + i;
				tempRoom.baseBet = 500;
				tempRoom.mission = true;
				tempRoom.fastRoom = true;
				tempRoom.seat1 = 100;
				tempRoom.seat2 = 101;
				tempRoom.seat3 = 103;
				tempRoom.nickname1 = "Fake1";
				tempRoom.nickname2 = "Fake2";
				tempRoom.nickname3 = "Fake3";
				tempRoom.level1 = 10;
				tempRoom.level2 = 20;
				tempRoom.level3 = 30;
				tempRoom.vipGrade1 = 10;
				tempRoom.vipGrade2 = 20;
				tempRoom.vipGrade3 = 30;
				if (i > 150)
				{
					tempRoom.seat4 = 104;
					tempRoom.nickname4 = "Fake4";
					tempRoom.level4 = 40;
					tempRoom.vipGrade4 = 40;
				}
				publicRoom.get(1).put(tempRoom.roomKey, tempRoom);
			}
			*/

			LoadPushEventData();
			LoadHotTimeEventData();			
			trace(ExtensionLogLevel.INFO, "CapsaSusun MasterExtension Server Ready.");
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("ServerReady Error : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void NettyMessageReceived(MessageEvent e)
	{
		String incomingMessage = (String)e.getMessage();
		LogMessage(String.format("NettyMessageReceived : %s", incomingMessage));
		try
		{
			ISFSObject requestObj = SFSObject.newFromJsonData(incomingMessage);
			String action = requestObj.getUtfString(ConstantClass.ACTION);
			switch (action)
			{
				// Game Server
			 	case ConstantClass.GAME_SERVER_CONNECTED:
				 	SetGameServer(requestObj, e.getChannel());
					break;
			 	case ConstantClass.GAME_SERVER_INFORMATION:
				 	UpdateGameServerInfo(requestObj);
				 	break;
				 // Room Event
			 	case ConstantClass.GAME_SERVER_CREATE_ROOM_FAILED:
			 		GameServerCreateRoomFailed(requestObj);
			 		break;
			 	case ConstantClass.GAME_SERVER_CREATE_ROOM_SUCCESS:
			 		GameServerCreateRoomSuccess(requestObj);
			 		break;
			 	case ConstantClass.GAME_SERVER_DELETE_ROOM:
			 		GameServerDeleteRoom(requestObj);
			 		break;
			 	case ConstantClass.GAME_SERVER_UPDATE_ROOM_STATUS:
			 		GameServerRoomStatusChange(requestObj);
			 		break;
			 	case ConstantClass.GAME_SERVER_UPDATE_GAME_RESULT:
			 		GameServerRoomFinishMatch(requestObj);
			 		break;
				case ConstantClass.GAME_SERVER_UPDATE_TOURNAMENT_RESULT:
			 		UserFinishTournamentRoom(requestObj);
			 		break;
			 	case ConstantClass.GAME_SERVER_CHECK_ROOM_EXIST:
			 		RemoveNotExistRoom(requestObj.getUtfString(ConstantClass.ROOM_LIST)); 
			 		break;
			 	case ConstantClass.GAME_SERVER_RESYNC_ROOM:
			 		GameServerResyncRoom(requestObj);
			 		break;
			 	case ConstantClass.USER_ENTER_ROOM:
			 		UserEnterRoom(requestObj);
			 		break;
			 	case ConstantClass.USER_LEAVE_ROOM:
			 		UserLeaveRoom(requestObj);
			 		break;
			 	case ConstantClass.USER_UPDATE_LEAGUE:
			 		GameServerUserUpdateLeague(requestObj);
			 		break;
			 	case ConstantClass.UPDATE_JACKPOT:
			 		UpdateAndSaveJackpot(requestObj.getLong(ConstantClass.JACKPOT));
			 		break;
			 	case ConstantClass.WIN_JACKPOT:
			 		JackpotWins(requestObj);
			 		break;
			 		
			 		
			 		
			 	default:
			 		trace(ExtensionLogLevel.ERROR, String.format("NettyMessageReceived Unknown Message : %s", incomingMessage));
			 		break;
			 }
		 }
		 catch(Exception ex)
		 {
			 trace(ExtensionLogLevel.ERROR, String.format("NettyMessageReceived : %s.\nInput Message : %s.", ErrorClass.StackTraceToString(ex), incomingMessage));
		 }
	}
	
	/*
	 * Common Functions
	 */

	public long GetJackpot()
	{
		return _jackpot.get();
	}
	
	public void LogMessage(ExtensionLogLevel level, String message)
	{
		if (!_showDetailLog && (level != ExtensionLogLevel.ERROR))
		{
			return;
		}
		trace(level, String.format("MasterExtension : %s", message));
	}
	
	public void LogMessage(String message)
	{
		LogMessage(ExtensionLogLevel.INFO, message);
	}
	
	private void MakeExpTable()
	{
		try
		{
			_expTable.clear();
			double baseExp = 300;
			double totalExp = 0;
			_expTable.add(0L);
			for(int i = 0; i < 99; i++)
			{
				if ( i > 0)
				{
					baseExp = baseExp + (baseExp * 0.17f);
				}
				totalExp += baseExp;
				_expTable.add(Math.round(totalExp));
			}
			LogMessage(String.format("Exp Table : %s",_expTable.toString()));
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("MakeExpTable Error : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public int GetLevel(long exp)
	{
		int temp = -1;
		for(int i = 0; i < 100; i++)
		{
			if ( exp >= _expTable.get(i))
			{
				temp = i;
			}
			else
			{
				break;
			}
		}
		return (temp + 1);
	}
	
	public void SendErrorMessage(String command, String message, boolean isTournament, User user)
	{
		if (user != null)
		{
			ISFSObject sendObj = new SFSObject();
			sendObj.putUtfString(ConstantClass.ERROR_CODE, message);
			sendObj.putUtfStringArray(ConstantClass.ERROR_MESSAGE, ErrorClass.GetErrorMessage(message));
			sendObj.putBool(ConstantClass.IS_TOURNAMENT, isTournament);
			this.send(command, sendObj, user);
		}
	}
	
	public int GetBetGrade(long bet)
	{
		int temp = -1;
		for(int i = 0; i < baseBet.length; i++)
		{
			if(bet == baseBet[i])
			{
				temp = i;
				break;
			}
		}
		return temp;
	}

	private int RandomInt(int Min, int Max)
	{
	     return (int)(Math.random()*(Max-Min))+Min;
	}
		
	public AsyncHttp getAsyncHttp()
	{
		return _async_http;
	}
	
	
	
	/*
	 * User Functions
	 */
	
	public boolean IsUserOnline(String playerName)
	{
		boolean temp = true;
		User checkUser = this.getParentZone().getUserByName(playerName);
		if (checkUser == null)
		{
			temp = false;
		}
		return temp;
	}
	
	public boolean IsUserPlayingGame(String playerName)
	{
		boolean temp = false;
		int playerID = Integer.parseInt(playerName);
		if (userData.containsKey(playerID))
		{
			if (userData.get(playerID).currentRoom >= 0)
			{
				temp = true;
			}
		}
		return temp;
	}
	
	public void UserFirstLoad(User user)
	{
		try
		{
			int playerID = Integer.parseInt(user.getName());
			LoadUserData(playerID);
			LoadUserFriendData(playerID);
		}
		catch (Exception e)
		{
			KickUser(user, "Failed load User data.", 1);
			trace(ExtensionLogLevel.ERROR, String.format("UserFirstLoad : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public void LoadUserData(int playerID) throws Exception
	{
		SusunUserData data = userData.get(playerID);
		if (data == null)
		{
			throw new Exception(String.format("LoadUserData : PlayerID %d Data is Null", playerID));
		}
		DBManager dm = DBManager.getInstance();
		Object[] obj = dm.GetUserData(playerID);
		if (obj !=null)
		{
			data.UpdateDataFromDatabase(obj);
		}
		else
		{
			throw new Exception(String.format("LoadUserData : PlayerID %d Load Database Failed", playerID));
		}
	}
	
	public void LoadUserFriendData(int playerID) throws Exception
	{
		SusunUserData data = userData.get(playerID);
		if (data == null)
		{
			throw new Exception(String.format("LoadUserFriendData : PlayerID %d Data is Null", playerID));
		}
		DBManager dm = DBManager.getInstance();
		List<Object[]> result =  dm.LoadSimpleStoredProcedure("sp_sfs_load_user_friend", playerID);
		for(Object[] obj : result)
		{
        	int pid =  Integer.parseInt(obj[0].toString());
        	int fid =  Integer.parseInt(obj[1].toString());
        	String pnm = obj[2].toString();
        	String fnm = obj[3].toString();
        	if (playerID == pid)
        	{
        		data.friendList.put(fid, fnm);
        	}
        	else if (playerID == fid)
        	{
        		data.friendList.put(pid, pnm);
        	}
		}
	}
		
	public void CheckUserAchievement(int playerID, int category, int value)
	{
		try
		{
			DBManager dm = DBManager.getInstance();
			List<Object[]> result =  dm.CheckAchievement(category, value, playerID);
			for(Object[] obj : result)
			{
				int achievementID = Integer.parseInt(obj[0].toString());
				String rewards = obj[1].toString();
				int retIns = dm.InsertUserAchivement(playerID, achievementID);
				if(retIns != 0)
				{
					trace(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : InsertUserAchivement Player %d, achievementID %d Failed, Flag %d.", playerID, achievementID, retIns));
					continue;
				}
	           	ISFSObject decodeRewards = SFSObject.newFromJsonData(String.format("{data:%s}", rewards));
	           	for(int j = 0; j < decodeRewards.getSFSArray("data").size(); j++)
	        	{
	           		int rewardType = decodeRewards.getSFSArray("data").getSFSObject(j).getInt("RewardType");
					int rewardValue = decodeRewards.getSFSArray("data").getSFSObject(j).getInt("RewardValue"); 
					String achievementMessage = String.format("{\"Type\":0,\"ID\":%d,\"Message\":\"Complete Achievement reward.\"}", achievementID);
					retIns = dm.InsertUserGift(playerID, 0, rewardType, rewardValue, achievementMessage);
					if(retIns != 0)
					{
						trace(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : InsertUserGift Player %d, achievementID %d Failed.", playerID, achievementID));
					}
	        	}
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public void ApproveFriendRequest(int playerID, int targetID, int targetFriendCount, String targetNickname) throws Exception
	{
		userData.get(playerID).friendList.put(targetID, targetNickname);    	
    	if(userData.containsKey(targetID))
		{
    		String playerNickname = userData.get(playerID).nickname;
    		userData.get(targetID).friendList.put(playerID, playerNickname);
 			User targetUser = this.getParentZone().getUserByName(Integer.toString(targetID));
			if(targetUser != null)
			{
				ISFSObject targetObj = new SFSObject();
				targetObj.putUtfString(ConstantClass.NICKNAME, playerNickname);
				targetObj.putInt(ConstantClass.USER_ID, playerID);
				targetObj.putInt(ConstantClass.GENDER, userData.get(playerID).gender);
				targetObj.putInt(ConstantClass.LEVEL, GetLevel(userData.get(playerID).exp));
				targetObj.putInt(ConstantClass.LEAGUE_GRADE, userData.get(playerID).leagueGrade);
				targetObj.putInt(ConstantClass.LEAGUE_DIVISION, userData.get(playerID).leagueDivision);
				this.send(ConstantClass.APPROVED_FRIEND_REQUEST, targetObj, targetUser);
			}
		}
    	CheckUserAchievement(targetID, 5, targetFriendCount);
	}
	
	public void KickUser(User user, String reason, int delay)
	{
		try
		{
			if ( user != null)
			{
				this.getApi().kickUser(user, null, reason, delay);
				LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %s kicked, reason : %s.", user.getName(), reason));
			}
		}
		catch (Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("KickUser : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public User FindOperatorUser() throws Exception
	{
		for(int i = 5; i <= 10; i++)
		{
			User checkUser = this.getParentZone().getUserByName(String.valueOf(i));
			if (checkUser != null)
			{
				return checkUser;
			}
		}
		return null;
	}
	
	public List<User> GetAllOperatorUser() throws Exception
	{
		List<User> temp = new ArrayList<User>();
		for(int i = 5; i <= 10; i++)
		{
			User checkUser = this.getParentZone().getUserByName(String.valueOf(i));
			if (checkUser != null)
			{
				temp.add(checkUser);
			}
		}
		return temp;
	}
	
	public boolean isOperatorUser(User user)
	{
		int playerID = Integer.parseInt(user.getName());
		if ((playerID >= 5) && (playerID <= 10))
		{
			return true;
		}
		return false;
	}
	
	
	/*
	 * Game Server Functions
	 */
	
	private void UpdateGameServerInfo(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int totalUser = data.getInt(ConstantClass.TOTAL_ALL_USERS);
		int totalRoom = data.getInt(ConstantClass.TOTAL_ALL_ROOMS);
		_gameServerTotalUser.put(host, totalUser);
		_gameServerTotalRoom.put(host, totalRoom);
	}
	
	private String FindAvailableGameHost()
	{
		String tempHost = "-";
		int tempTotalRoom = Integer.MAX_VALUE;
		try
		{
			for(String host : _gameServer.keySet())
			{
				if ((_gameServerLimit.get(host) == 0) || (_gameServerTotalUser.get(host) < _gameServerLimit.get(host)))
				{
					if (_gameServerTotalRoom.get(host) < tempTotalRoom)
					{
						tempHost = host;
						tempTotalRoom = _gameServerTotalRoom.get(host);
					}
				}
			}
		}
		catch(Exception e)
		{
			tempHost = "-";
			trace(ExtensionLogLevel.ERROR, String.format("FindAvailableGameHost Error : %s.", ErrorClass.StackTraceToString(e)));
		}
		return tempHost;
	}
	
	private void ClearGameServerRoomInfo(String host) throws Exception
	{
		int totalBetGrade = baseBet.length;
		for(int i = 0; i < totalBetGrade; i++)
		{
			RemoveRoomWithHost(publicRoom.get(i), host);
			RemoveRoomWithHost(vipRoom.get(i), host);
		}
		for(int i = 1; i <= 3; i++)
		{
			RemoveRoomWithHost(tournamentRoom.get(i), host);
		}
	}
	
	private void SetGameServer(ISFSObject data, Channel channel) throws Exception
	{
		String remoteAddr = channel.getRemoteAddress().toString();
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int limit = data.getInt(ConstantClass.CCU_LIMIT);
		
		if (!remoteAddr.contains(host) || !allowedGameServer.contains(host))
		{
			channel.close();
			trace(ExtensionLogLevel.ERROR, String.format("SetGameServer : %s not allowed to connect.", remoteAddr));
			return;
		}
		
		ClearGameServerRoomInfo(host);		
		_gameServer.put(host, channel);
		_gameServerLimit.put(host, limit);
		_gameServerTotalUser.put(host, 0);
		_gameServerTotalRoom.put(host, 0);
		
		ISFSObject sendData = new SFSObject();
		sendData.putUtfString(ConstantClass.ACTION, ConstantClass.MASTER_SERVER_INFORMATION);
		sendData.putBool(ConstantClass.LOG_STATUS, _showDetailLog);
		sendData.putBool(ConstantClass.LEAGUE_STATUS, _leagueStatus);
		WriteToGameServer(host, sendData.toJson());
		trace(ExtensionLogLevel.INFO, String.format("GameServer : %s connected.", host));
	}
		
	private void RemoveGameServer(Channel channel)
	{
		try
		{
			String host = "";
			for (ConcurrentHashMap.Entry<String, Channel> entry : _gameServer.entrySet())
			{
			    String key = entry.getKey();
			    Channel value = entry.getValue();
			    if (value == channel)
			    {
			    	host = key;
			    	break;
			    }
			}
			if (host.isEmpty())
			{
				trace(ExtensionLogLevel.INFO, String.format("RemoveGameServer : GameServer : %s not found.", channel.getRemoteAddress().toString()));
			}
			else
			{
				_gameServer.remove(host);
				_gameServerLimit.remove(host);
				_gameServerTotalUser.remove(host);
				_gameServerTotalRoom.remove(host);
				ClearGameServerRoomInfo(host);
				trace(ExtensionLogLevel.INFO, String.format("CapsaSusun MasterExtension GameServer : %s removed.", host));
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("RemoveGameServer : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void NotifyAllGameServer(ISFSObject data) throws Exception
	{
		for (ConcurrentHashMap.Entry<String, Channel> entry : _gameServer.entrySet())
		{
			WriteToGameServer(entry.getKey(), data.toJson());
		}
	}
		
	private void WriteToGameServer(String host, String message)
	{
		if (_gameServer.containsKey(host))
		{
			_gameServer.get(host).write(message + "\r\n");
			LogMessage(String.format("WriteToGameServer : %s, Message : %s", host, message));
		}
		else
		{
			trace(ExtensionLogLevel.ERROR, String.format("WriteToGameServer : Host %s not found, Message : $s", host, message));
		}
	}
	
	/*
	 * Game Room Functions
	 */
	
	private int GenerateRoomKey() throws Exception
	{
		int roomKey = -1;
		boolean sameKey = true;
		while(sameKey)
		{
			roomKey = RandomInt(1000000,1000000000);
			sameKey = false;
			int totalBetGrade = baseBet.length;
			for(int i = 0; i < totalBetGrade; i++)
			{
				if (publicRoom.get(i).containsKey(roomKey))
				{
					sameKey = true;
					break;
				}
				if (vipRoom.get(i).containsKey(roomKey))
				{
					sameKey = true;
					break;
				}
			}
			if (!sameKey)
			{
				for(int i = 1; i <= 3; i++)
				{
					if (tournamentRoom.get(i).containsKey(roomKey))
					{
						sameKey = true;
						break;
					}
				}
			}
			if (!sameKey)
			{
				if(_userWaitingRoomInfo.containsKey(roomKey))
				{
					sameKey = true;
				}
			}
		}
		return roomKey;
	}

	private void RemoveRoomWithHost(ConcurrentHashMap<Integer, SusunRoomInfo> roomList, String host) throws Exception
	{
		ArrayList<Integer> removeRoomList = new ArrayList<Integer>();
		for (ConcurrentHashMap.Entry<Integer, SusunRoomInfo> entry : roomList.entrySet())
		{
			int roomKey = entry.getKey();
			SusunRoomInfo roomInfo = entry.getValue();
		    if ( roomInfo.host.equals(host))
		    {
		    	removeRoomList.add(roomKey);
		    }
		}
		for(int j = 0; j < removeRoomList.size(); j++)
		{
			int roomKey = removeRoomList.get(j);
			roomList.remove(roomKey);
		}
	}
	
	private int FindUserInRoomList(ConcurrentHashMap<Integer, SusunRoomInfo> tempRoom, int playerID)
	{
		for (ConcurrentHashMap.Entry<Integer, SusunRoomInfo> entry : tempRoom.entrySet())
		{
			int roomKey = entry.getKey();
			SusunRoomInfo roomInfo = entry.getValue();
		    if ((roomInfo.seat1 == playerID) || (roomInfo.seat2 == playerID) || (roomInfo.seat3 == playerID) || (roomInfo.seat4 == playerID) )
		    {
		    	return roomKey;
		    }
		}
		
		return -1;
	}
	
	public ISFSObject FindUserInAllRoom(int playerID)
	{
		ISFSObject returnObj = new SFSObject();
		int temp = -1;
		String host = "";
		boolean isTournament = false;
		try
		{
			int totalBetGrade = baseBet.length;
			ConcurrentHashMap<Integer, SusunRoomInfo> tempRoom = null;
			for(int i = 0; i < totalBetGrade; i++)
			{
				tempRoom = publicRoom.get(i);
				temp = FindUserInRoomList(tempRoom, playerID);
				if (temp != -1)
				{
					host = tempRoom.get(temp).host;
					break;
				}
				tempRoom = vipRoom.get(i);
				temp = FindUserInRoomList(tempRoom, playerID);
				if (temp != -1)
				{
					host = tempRoom.get(temp).host;
					break;
				}
			}
			if (temp == -1)
			{
				for(int i = 1; i <= 3; i++)
				{
					tempRoom = tournamentRoom.get(i);
					temp = FindUserInRoomList(tempRoom, playerID);
					if (temp != -1)
					{
						isTournament = true;
						host = tempRoom.get(temp).host;
						break;
					}
				}
			}
		}
		catch (Exception e)
		{
			temp = -1;
			host = "";
			isTournament = false;
			trace(ExtensionLogLevel.ERROR, String.format("FindUserInAllRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
		returnObj.putInt(ConstantClass.ROOM_KEY, temp);
		returnObj.putUtfString(ConstantClass.GAME_SERVER_HOST, host);
		returnObj.putBool(ConstantClass.IS_TOURNAMENT, isTournament);
		return returnObj;
	}
	
	private void AskResyncGameRoom(String host, int roomKey)
	{
		try
		{
			ISFSObject sendObj = new SFSObject();
			sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_RESYNC_ROOM);
			sendObj.putInt(ConstantClass.ROOM_KEY, roomKey);
			WriteToGameServer(host, sendObj.toJson());
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("AskResyncGameRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
		
	public void UserCreateRoom(int playerID, int betGrade, boolean hasMission, boolean isVip, boolean fastRoom) throws Exception
	{
		userData.get(playerID).currentRoom = 0;
		ISFSObject data = new SFSObject();
		data.putInt(ConstantClass.BASE_BET, betGrade);
		data.putBool(ConstantClass.HAS_MISSION, hasMission);
		data.putBool(ConstantClass.IS_VIP, isVip);
		data.putBool(ConstantClass.FAST_ROOM, fastRoom);
		data.putBool(ConstantClass.IS_TOURNAMENT, false);
		createRoomList.put(playerID, data);
	}
	
	public void UserCreateTournamentRoom(int playerID, int tournamentGrade) throws Exception
	{
		userData.get(playerID).currentRoom = 0;
		ISFSObject data = new SFSObject();
		data.putInt(ConstantClass.TOURNAMENT_GRADE, tournamentGrade);
		data.putBool(ConstantClass.IS_TOURNAMENT, true);
		createRoomList.put(playerID, data);
	}

	public void RemoveUserInFindRoomInfo(String userName)
	{
		try
		{
			int totalBetGrade = baseBet.length;
			for(int i = 0; i < totalBetGrade; i++)
			{
				FindRoomUserInfo.get(i).remove(userName);
			}
		}
		catch (Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("RemoveUserInFindRoomInfo : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void AlertFindRoomUser(int betGrade, int roomKey, SusunRoomInfo roomInfo, boolean deleteRoom)
	{
		try
		{
			if (roomInfo.isVip)
			{
				return;
			}
			if (betGrade >= 0)
			{
				ArrayList<String> tempList = new ArrayList<String>(FindRoomUserInfo.get(betGrade));
				SmartFoxServer.getInstance().getTaskScheduler().schedule(new FindRoomAlert(roomKey, deleteRoom, roomInfo, tempList), 5, TimeUnit.MILLISECONDS);
			}
			else
			{
				trace(ExtensionLogLevel.ERROR, String.format("AlertFindRoomUser Invalid Data, betGrade %d, roomKey %d, roomInfo %s", betGrade, roomKey, roomInfo.ConvertToSmartfoxObject().toJson()));
			}
		}
		catch (Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("AlertFindRoomUser : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public SusunRoomInfo GetRoomInfo(int roomKey, int betGrade) throws Exception
	{
		if(publicRoom.get(betGrade).containsKey(roomKey))
		{
			return publicRoom.get(betGrade).get(roomKey);
		}
		if(vipRoom.get(betGrade).containsKey(roomKey))
		{
			return vipRoom.get(betGrade).get(roomKey);
		}
		return null;
	}
	
	private void GameServerCreateRoomFailed(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		trace(ExtensionLogLevel.ERROR, String.format("GameServer %s failed to create Room %d.", host, roomKey));
		
		if ( !_userWaitingRoomInfo.containsKey(roomKey))
		{
			trace(ExtensionLogLevel.ERROR, String.format("GameServerFailedCreateRoom : WaitRoomInfo not contains %d.", roomKey));
			return;
		}
		int creator = _userWaitingRoomInfo.remove(roomKey);
		if (userData.containsKey(creator))
		{
			userData.get(creator).currentRoom = -1;
			SendErrorMessage(ConstantClass.ROOM_TO_ENTER, ErrorClass.SERVER_UNKNOWN_ERROR, isTournament, this.getParentZone().getUserByName(Integer.toString(creator)));
		}
	}
	
	private void GameServerCreateRoomSuccess(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		if (!_userWaitingRoomInfo.containsKey(roomKey))
		{
			trace(ExtensionLogLevel.ERROR, String.format("GameServerCreateRoomSuccess : WaitRoomInfo not contains %d.", roomKey));
			return;
		}
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			if((tournamentGrade < 1) || (tournamentGrade > 3))
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerCreateRoomSuccess : invalid Tournament Grade %d for RoomKey %d.", tournamentGrade, roomKey));
				return;
			}
			
			SusunRoomInfo roomInfo = new SusunRoomInfo(data, baseBet[0]);
			tournamentRoom.get(tournamentGrade).put(roomKey, roomInfo);
			LogMessage(String.format("Tournament Room %d created in GameServer %s.", roomKey, host));
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			boolean isVip = data.getBool(ConstantClass.IS_VIP);
			if(betGrade < 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerCreateRoomSuccess : invalid betGrade %d for RoomKey %d.", betGrade, roomKey));
				return;
			}

			SusunRoomInfo roomInfo = new SusunRoomInfo(data, baseBet[betGrade]);
			if(isVip)
			{
				vipRoom.get(betGrade).put(roomKey, roomInfo);
			}
			else
			{
				publicRoom.get(betGrade).put(roomKey, roomInfo);
				
			}
			AlertFindRoomUser(betGrade, roomKey, roomInfo, false);
			LogMessage(String.format("Room %d created in GameServer %s.", roomKey, host));
		}
		int creator = _userWaitingRoomInfo.remove(roomKey);
		
		
		User user = this.getParentZone().getUserByName(Integer.toString(creator));
		if (user != null)
		{
			ISFSObject sendObj = new SFSObject();
			sendObj.putUtfString(ConstantClass.GAME_SERVER_HOST, host);
			sendObj.putInt(ConstantClass.ROOM_KEY, roomKey);
			sendObj.putBool(ConstantClass.IS_TOURNAMENT, isTournament);
			this.send(ConstantClass.ROOM_TO_ENTER, sendObj, user);
		}
		else
		{
			trace(ExtensionLogLevel.ERROR, String.format("GameServerCreateRoomSuccess : User not found : %d for RoomKey %d.", creator, roomKey));
		}
	}
	
	private void GameServerDeleteRoom(ISFSObject data) throws Exception
	{
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			RemoveTournamentRoomInfo(roomKey, tournamentGrade);
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			boolean isVip = data.getBool(ConstantClass.IS_VIP);
			RemoveRoomInfo(roomKey, betGrade, isVip);
		}
	}
	
	private void UserEnterRoom(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		int playerID = data.getInt(ConstantClass.USER_ID);
		int gender = data.getInt(ConstantClass.GENDER);
		int level = data.getInt(ConstantClass.LEVEL);
		int vipGrade = data.getInt(ConstantClass.VIP_GRADE);
		int leagueGrade = data.getInt(ConstantClass.LEAGUE_GRADE);
		int leagueDivision = data.getInt(ConstantClass.LEAGUE_DIVISION);
		int seatNumber = data.getInt(ConstantClass.SEAT_INDEX);
		String nickname = data.getUtfString(ConstantClass.NICKNAME);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		
		
		RemoveUserInFindRoomInfo(Integer.toString(playerID));
		if(userData.containsKey(playerID))
		{
			userData.get(playerID).currentRoom = roomKey;
		}
		
		SusunRoomInfo roomInfo = null;
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			if((tournamentGrade < 1) || (tournamentGrade > 3))
			{
				trace(ExtensionLogLevel.ERROR, String.format("UserEnterRoom : Tournament Grade %d not found for RoomKey %d..", tournamentGrade, roomKey));
				return;
			}
			if (tournamentRoom.get(tournamentGrade).containsKey(roomKey))
			{
				roomInfo = tournamentRoom.get(tournamentGrade).get(roomKey);
				roomInfo.UpdateSeat(seatNumber, playerID, gender, level, vipGrade, leagueGrade, leagueDivision, nickname); 
				LogMessage(String.format("PlayerID %d : %s Enter Tournament Room %d.", playerID, nickname, roomKey));
			}
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			if(betGrade < 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("UserEnterRoom : BetGrade %d not found for RoomKey %d..", betGrade, roomKey));
				return;
			}
			roomInfo = GetRoomInfo(roomKey, betGrade);
			if (roomInfo != null)
			{
				roomInfo.UpdateSeat(seatNumber, playerID, gender, level, vipGrade, leagueGrade, leagueDivision, nickname);
				LogMessage(String.format("PlayerID %d : %s Enter Room %d.", playerID, nickname, roomKey));
				AlertFindRoomUser(betGrade, roomKey, roomInfo, false);
			}
		}
		if (roomInfo == null)
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("UserEnterRoom : RoomKey %d not found.", roomKey));
		}
	}
		
	private void UserLeaveRoom(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		int playerID = data.getInt(ConstantClass.USER_ID);
		int seatNumber = data.getInt(ConstantClass.SEAT_INDEX);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		
		if(userData.containsKey(playerID))
		{
			userData.get(playerID).currentRoom = -1;
		}
		
		SusunRoomInfo roomInfo = null;
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			if((tournamentGrade < 1) || (tournamentGrade > 3))
			{
				trace(ExtensionLogLevel.ERROR, String.format("UserLeaveRoom : Tournament Grade %d not found for RoomKey %d..", tournamentGrade, roomKey));
				return;
			}
			if (tournamentRoom.get(tournamentGrade).containsKey(roomKey))
			{
				roomInfo = tournamentRoom.get(tournamentGrade).get(roomKey);
				roomInfo.UpdateSeat(seatNumber, -1, 0, 0, 0, 0, 1,  "-"); 
				
				LogMessage(String.format("PlayerID %d Leave Tournament Room %d.", playerID, roomKey));
				if ((roomInfo.getTotalPlayer() == 0))
				{
					RemoveTournamentRoomInfo(roomKey, tournamentGrade);
				}
			}
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			boolean isVip = data.getBool(ConstantClass.IS_VIP);
			if(betGrade < 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("UserLeaveRoom : BetGrade %d not found for RoomKey %d..", betGrade, roomKey));
				return;
			}
			roomInfo = GetRoomInfo(roomKey, betGrade);
			if (roomInfo != null)
			{
				roomInfo.UpdateSeat(seatNumber, -1, 0, 0, 0, 0, 1, "-");
				LogMessage(String.format("PlayerID %d Leave Room %d.", playerID, roomKey));
				if (roomInfo.getTotalPlayer() == 0)
				{
					RemoveRoomInfo(roomKey, betGrade, isVip);
				}
				else
				{
					AlertFindRoomUser(betGrade, roomKey, roomInfo, false);
				}
			}
		}
		if (roomInfo == null)
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("UserLeaveRoom : RoomKey %d not found.", roomKey));
		}
	}
		
	private void UserFinishTournamentRoom(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		int playerID = data.getInt(ConstantClass.USER_ID);
		String[] stringCoin = data.getUtfString("StringCoin").split(":");
		long exp = Long.parseLong(stringCoin[0]);
		long coin = Long.parseLong(stringCoin[1]);
		int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
		int seatNumber = data.getInt(ConstantClass.SEAT_INDEX);

		if(userData.containsKey(playerID))
		{
			userData.get(playerID).currentRoom = -1;
			userData.get(playerID).exp = exp;
			userData.get(playerID).money = coin;
		}
		if((tournamentGrade < 1) || (tournamentGrade > 3))
		{
			trace(ExtensionLogLevel.ERROR, String.format("UserFinishTournamentRoom : Tournament Grade %d not found for RoomKey %d..", tournamentGrade, roomKey));
			return;
		}
		if (tournamentRoom.get(tournamentGrade).containsKey(roomKey))
		{
			SusunRoomInfo roomInfo = tournamentRoom.get(tournamentGrade).get(roomKey);
			roomInfo.UpdateSeat(seatNumber, -1, 0, 0, 0, 0, 1, "-"); 
			
			LogMessage(String.format("PlayerID %d Finish Tournament in Room %d.", playerID, roomKey));
			if ((roomInfo.getTotalPlayer() == 0))
			{
				RemoveTournamentRoomInfo(roomKey, tournamentGrade);
			}
		}
		else
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("UserFinishTournamentRoom : RoomKey %d not found.", roomKey));
		}
	}
	
	private void ForceRoomDelete(int roomKey) throws Exception
	{
		boolean isVip = false;
		int betGrade = -1;
		for(int i = 0; i < baseBet.length; i++)
		{
			if (publicRoom.get(i).containsKey(roomKey))
			{
				betGrade = i;
				isVip = false;
				break;
			}
			else if (vipRoom.get(i).containsKey(roomKey))
			{
				betGrade = i;
				isVip = true;
				break;
			}
		}
		if (betGrade >= 0)
		{
			RemoveRoomInfo(roomKey, betGrade, isVip);
		}
	}
	
	private void ForceTournamentRoomDelete(int roomKey) throws Exception
	{
		int tournamentGrade = 0;
		for(int i = 1; i <= 3; i++)
		{
			if (tournamentRoom.get(i).containsKey(roomKey))
			{
				tournamentGrade = i;
				break;
			}
		}
		if (tournamentGrade >= 1)
		{
			RemoveTournamentRoomInfo(roomKey, tournamentGrade);
		}
	}
	
	private void RemoveNotExistRoom(String roomList) throws Exception
	{
		ArrayList<String> splitRoom = new ArrayList<String>(Arrays.asList(roomList.split(":")));
		for(String roomName : splitRoom)
		{
			ForceRoomDelete(Integer.parseInt(roomName));
			ForceTournamentRoomDelete(Integer.parseInt(roomName));
		}
	}
	
	private void RemoveRoomInfo(int roomKey, int betGrade, boolean isVip) throws Exception
	{
		if(betGrade < 0)
		{
			trace(ExtensionLogLevel.ERROR, String.format("RemoveRoomInfo : BetGrade %d not found for RoomKey %d..", betGrade, roomKey));
			return;
		}
		ConcurrentHashMap<Integer, SusunRoomInfo> tempDelete = null;
		if (isVip)
		{
			tempDelete = vipRoom.get(betGrade);
		}
		else
		{
			tempDelete = publicRoom.get(betGrade);
		}
		if(tempDelete.containsKey(roomKey))
		{
			tempDelete.remove(roomKey);
			SusunRoomInfo delInfo = new SusunRoomInfo();
			delInfo.isVip = isVip;
			AlertFindRoomUser(betGrade, roomKey, delInfo, true);
			LogMessage(String.format("Room %d Deleted.", roomKey));
		}
	}
	
	private void RemoveTournamentRoomInfo(int roomKey, int tournamentGrade) throws Exception
	{
		if(tournamentGrade <= 0)
		{
			trace(ExtensionLogLevel.ERROR, String.format("RemoveTournamentRoomInfo : Tournament Grade %d not found for RoomKey %d.", tournamentGrade, roomKey));
			return;
		}
		if (tournamentRoom.get(tournamentGrade).containsKey(roomKey))
		{
			tournamentRoom.get(tournamentGrade).remove(roomKey);
			LogMessage(String.format("Tournament Room %d Deleted.", roomKey));
		}
	}
	
	private void GameServerRoomStatusChange(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		SusunRoomInfo roomInfo = null;
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			if(tournamentGrade <= 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerRoomStatusChange : Tournament Grade %d not found for RoomKey %d.", tournamentGrade, roomKey));
				return;
			}
			roomInfo = tournamentRoom.get(tournamentGrade).get(roomKey);
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			if(betGrade < 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerRoomStatusChange : BetGrade %d not found for RoomKey %d.", betGrade, roomKey));
				return;
			}
			roomInfo = GetRoomInfo(roomKey, betGrade);
		}
		if (roomInfo != null)
		{
			boolean isPlaying = data.getBool(ConstantClass.IS_ROOM_PLAYING);
			roomInfo.UpdateRoomStatus(isPlaying);
		}
		else
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("GameServerRoomStatusChange : RoomKey %d not found.", roomKey));
		}
	}
	
	private void GameServerRoomFinishMatch(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		int betGrade = data.getInt(ConstantClass.BASE_BET);
		String[] stringCoin1 = data.getUtfString("StringCoin1").split(":");
		String[] stringCoin2 = data.getUtfString("StringCoin2").split(":");
		String[] stringCoin3 = data.getUtfString("StringCoin3").split(":");
		String[] stringCoin4 = data.getUtfString("StringCoin4").split(":");
		int[] userID = new int[4];
		userID[0] = Integer.parseInt(stringCoin1[0]);
		userID[1] = Integer.parseInt(stringCoin2[0]);
		userID[2] = Integer.parseInt(stringCoin3[0]);
		userID[3] = Integer.parseInt(stringCoin4[0]);
		long[] coin = new long[4];
		coin[0] = Long.parseLong(stringCoin1[1]);
		coin[1] = Long.parseLong(stringCoin2[1]);
		coin[2] = Long.parseLong(stringCoin3[1]);
		coin[3] = Long.parseLong(stringCoin4[1]);
		long[] exp = new long[4];
		exp[0] = Long.parseLong(stringCoin1[2]);
		exp[1] = Long.parseLong(stringCoin2[2]);
		exp[2] = Long.parseLong(stringCoin3[2]);
		exp[3] = Long.parseLong(stringCoin4[2]);
		if(betGrade < 0)
		{
			trace(ExtensionLogLevel.ERROR, String.format("GameServerRoomFinishMatch : BetGrade %d not found for RoomKey %d..", betGrade, roomKey));
			return;
		}
		SusunRoomInfo roomInfo = GetRoomInfo(roomKey, betGrade);
		for(int i = 0; i < 4; i++)
		{
			int id = userID[i];
			if ( id >= 0)
			{
				if(userData.containsKey(id))
				{
					userData.get(id).exp = exp[i];
					userData.get(id).money = coin[i];
				}
				if(roomInfo != null)
				{
					if (roomInfo.seat1 == id)
			    	{
						roomInfo.level1 = GetLevel(exp[i]);
			    	}
					else if (roomInfo.seat2 == id)		
					{
						roomInfo.level2 = GetLevel(exp[i]);
			    	}
					else if (roomInfo.seat3 == id)
			    	{
						roomInfo.level3 = GetLevel(exp[i]);
			    	}
					else if (roomInfo.seat4 == id)
			    	{
						roomInfo.level4 = GetLevel(exp[i]);
			    	}
				}
			}
		}
		if(roomInfo != null)
		{
			roomInfo.updateTime = System.currentTimeMillis();
		}
		else
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("GameServerRoomFinishMatch : RoomKey %d not found.", roomKey));
		}
	}
	
	private void GameServerUserUpdateLeague(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		int betGrade = data.getInt(ConstantClass.BASE_BET);
		int seat = data.getInt(ConstantClass.SEAT_INDEX);
		int playerID = data.getInt(ConstantClass.USER_ID);
		int leagueGrade = data.getInt(ConstantClass.LEAGUE_GRADE);
		int leagueDivision = data.getInt(ConstantClass.LEAGUE_DIVISION);
		
		// Update User Data 1st
		SusunUserData ud = userData.get(playerID);
		if (ud != null)
		{
			ud.leagueGrade = leagueGrade;
			ud.leagueDivision = leagueDivision;
		}
		
		SusunRoomInfo roomInfo = GetRoomInfo(roomKey, betGrade);
		if (roomInfo != null)
		{
			if (roomInfo.getSeatPlayerID(seat) == playerID)
			{
				roomInfo.UpdateSeatLeague(seat, leagueGrade, leagueDivision);
			}
			else
			{
				LogMessage(ExtensionLogLevel.WARN, String.format("GameServerUserUpdateLeague : RoomKey %d seat %d has different user.", roomKey, seat));
			}
		}
		else
		{
			AskResyncGameRoom(host, roomKey);
			LogMessage(ExtensionLogLevel.WARN, String.format("GameServerUserUpdateLeague : RoomKey %d not found.", roomKey));
		}
	}
	
	private void GameServerResyncRoom(ISFSObject data) throws Exception
	{
		String host = data.getUtfString(ConstantClass.GAME_SERVER_HOST);
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		SusunRoomInfo roomInfo = null;
		if (isTournament)
		{
			int tournamentGrade = data.getInt(ConstantClass.TOURNAMENT_GRADE);
			if((tournamentGrade < 1) || (tournamentGrade > 3))
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerResyncRoom : invalid Tournament Grade %d for RoomKey %d.", tournamentGrade, roomKey));
				return;
			}
			roomInfo = new SusunRoomInfo(data, baseBet[0]);
			tournamentRoom.get(tournamentGrade).put(roomKey, roomInfo);
			LogMessage(String.format("Tournament Room %d Resync in GameServer %s.", roomKey, host));
		}
		else
		{
			int betGrade = data.getInt(ConstantClass.BASE_BET);
			boolean isVip = data.getBool(ConstantClass.IS_VIP);
			if(betGrade < 0)
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerResyncRoom : invalid betGrade %d for RoomKey %d.", betGrade, roomKey));
				return;
			}
			roomInfo = new SusunRoomInfo(data, baseBet[betGrade]);
			if(isVip)
			{
				vipRoom.get(betGrade).put(roomKey, roomInfo);
			}
			else
			{
				publicRoom.get(betGrade).put(roomKey, roomInfo);
			}
			LogMessage(String.format("Room %d Resync in GameServer %s.", roomKey, host));
		}
	}
	
	/*
	 * Create Room Cycle
	 */
	
	private class RoomCreationHandler implements Runnable
	{
		public void run()
		{
			try
			{
				ArrayList<Integer> listRequest = new ArrayList<Integer>(createRoomList.keySet());
				for(Integer playerID: listRequest)
				{
					ISFSObject createData = createRoomList.remove(playerID);
					if (createData == null)
					{
						trace(ExtensionLogLevel.ERROR, String.format("RoomCreationHandler : User %d don't have room create request.", playerID));
						continue;
					}
					User requester = SusunMasterExtension.this.getParentZone().getUserByName(playerID.toString());
					if (requester == null)
					{
						trace(ExtensionLogLevel.ERROR, String.format("RoomCreationHandler : User %d not found.", playerID));
						continue;
					}
					
					String SelectedHost = FindAvailableGameHost();
					if (_gameServer.containsKey(SelectedHost))
					{
						int roomKey = GenerateRoomKey();
						_userWaitingRoomInfo.put(roomKey, playerID);
						
						createData.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CREATE_ROOM);
						createData.putInt(ConstantClass.ROOM_KEY, roomKey);
						createData.putInt(ConstantClass.ROOM_CREATOR, playerID);
						WriteToGameServer(SelectedHost, createData.toJson());
					}
					else
					{
						ISFSObject sendObj = new SFSObject();
						sendObj.putUtfString(ConstantClass.ERROR_CODE, ErrorClass.GAME_SERVER_FULL);
						sendObj.putUtfStringArray(ConstantClass.ERROR_MESSAGE, ErrorClass.GetErrorMessage(ErrorClass.GAME_SERVER_FULL));
						sendObj.putBool(ConstantClass.IS_TOURNAMENT, createData.getBool(ConstantClass.IS_TOURNAMENT));
						SusunMasterExtension.this.send(ConstantClass.ROOM_TO_ENTER, sendObj, requester);
						trace(ExtensionLogLevel.ERROR, "RoomCreationHandler : No Available Game Server.");
					}
				}
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("RoomCreationHandler : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	/*
	 * Room Idle Check Cycle
	 */
	
	private class RoomIdleCheckHandler implements Runnable
	{
		public void run()
		{
			try
			{
				// Non Tournament Room Idle Check
				ConcurrentHashMap<String, ArrayList<String>> tempAskList = new ConcurrentHashMap<String, ArrayList<String>>();
				for(String serverName : _gameServer.keySet())
				{
					ArrayList<String> tempList = new ArrayList<String>();
					tempAskList.put(serverName, tempList);
				}
				long systemTime = System.currentTimeMillis();
				for(int i = 0; i < baseBet.length; i++)
				{
					ConcurrentHashMap<Integer, SusunRoomInfo> tempPublic = publicRoom.get(i);
					for (ConcurrentHashMap.Entry<Integer, SusunRoomInfo> entry : tempPublic.entrySet())
					{
						int roomKey = entry.getKey();
						SusunRoomInfo roomInfo = entry.getValue();
					    long updateTime = roomInfo.updateTime;
					    if ((systemTime - updateTime) > 150000)
					    {
					    	String host = roomInfo.host;
					    	tempAskList.get(host).add(Integer.toString(roomKey));
					    }
					}
					ConcurrentHashMap<Integer, SusunRoomInfo> tempVip = vipRoom.get(i);
					for (ConcurrentHashMap.Entry<Integer, SusunRoomInfo> entry : tempVip.entrySet())
					{
						int roomKey = entry.getKey();
						SusunRoomInfo roomInfo = entry.getValue();
					    long updateTime = roomInfo.updateTime;
					    if ((systemTime - updateTime) > 150000)
					    {
					    	String host = roomInfo.host;
					    	tempAskList.get(host).add(Integer.toString(roomKey));
					    }
					}
				}
				for(String serverName : tempAskList.keySet())
				{
					ArrayList<String> tempList = tempAskList.get(serverName);
					int roomSize = tempList.size();
					if(roomSize > 0)
					{
						String listRoom = "";
						for(int j = 0; j < roomSize; j++)
						{
							if (j == 0)
							{
								listRoom = tempList.get(j);
							}
							else
							{
								listRoom = String.format("%s:%s", listRoom, tempList.get(j));
							}
							if (j > 10)
							{
								break;
							}
						}
						ISFSObject sendObj = new SFSObject();
						sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CHECK_ROOM_EXIST);
						sendObj.putUtfString(ConstantClass.ROOM_LIST, listRoom);
						WriteToGameServer(serverName, sendObj.toJson());
					}
				}
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("RoomIdleCheckHandler : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	/*
	 * Find Room Alert Cycle
	 */
	
	private class FindRoomAlert implements Runnable
	{
		final int roomKey;
		final SusunRoomInfo roomData;
		final boolean isDelete;
		final ArrayList<String> userList;
		
		public FindRoomAlert(int key, boolean del, SusunRoomInfo sendData, ArrayList<String> alertList)
		{
			roomKey = key;
			isDelete = del;
			roomData = sendData;
			userList = alertList;
		}
		
		public void run()
		{
			try
			{
				ArrayList<User> sendList = new ArrayList<User>();
				for(String username : userList)
				{
					if ((username != null) && (username.trim().length() > 0))
					{
						User cekUser = SusunMasterExtension.this.getApi().getUserByName(username);
						if(cekUser != null)
						{
							sendList.add(cekUser);
						}
					}
				}
				ISFSObject informObj = new SFSObject();
				informObj.putInt(ConstantClass.ROOM_KEY, roomKey);
				informObj.putSFSObject(ConstantClass.ROOM_INFORMATION, roomData.ConvertToSmartfoxObject());
				informObj.putBool(ConstantClass.IS_ROOM_DELETED, isDelete);
				SusunMasterExtension.this.send(ConstantClass.ROOM_INFO_CHANGED, informObj, sendList);
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("FindRoomAlert : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class JackpotAlert implements Runnable
	{
		public void run()
		{
			try
			{
				ISFSObject data = new SFSObject();
				data.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_JACKPOT);
				data.putLong(ConstantClass.JACKPOT, _jackpot.get());
				NotifyAllGameServer(data);
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("JackpotAlert : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	
	
	
			
	/*
	 * Servlet
	 */

	private String UpdateDetailLogStatus(String status)
	{
		String temp = "";
		try
		{
			_showDetailLog = Boolean.parseBoolean(status);
			temp = String.format("Show Detail Log set to : %s.", _showDetailLog);
			
			ISFSObject sendData = new SFSObject();
			sendData.putUtfString(ConstantClass.ACTION, ConstantClass.MASTER_SERVER_INFORMATION);
			sendData.putBool(ConstantClass.LOG_STATUS, _showDetailLog);
			sendData.putBool(ConstantClass.LEAGUE_STATUS, _leagueStatus);
			NotifyAllGameServer(sendData);
			
			trace(temp);
		}
		catch(Exception e)
		{
			temp = "Unknown Server Error, please try again.";
			trace(ExtensionLogLevel.ERROR, String.format("GetServerInfoJson : %s.", ErrorClass.StackTraceToString(e)));
		}
		return temp;
	}
	
	private String GetServerInfoJson()
	{
		ISFSObject tempObj = new SFSObject();
		try
		{
			Calendar now = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzzz");
			int totalUser = 0;
			for(Integer tu : _gameServerTotalUser.values())
			{
				totalUser += tu;
			}
			int totalRoom = 0;
			for(Integer roomTotal : _gameServerTotalRoom.values())
			{
				totalRoom += roomTotal;
			}
			int totalTournament = 0;
			for(int i = 1; i <= 3; i++)
			{
				totalTournament += tournamentRoom.get(i).size();
			}
			int totalPrivate = 0;
			int totalVip = 0;
			int totalBetGrade = baseBet.length;
			for(int i = 0; i < totalBetGrade; i++)
			{
				totalVip += vipRoom.get(i).size();
			}
			tempObj.putUtfString("Extension", "SusunMasterExtension");
			tempObj.putUtfString("Version", version);
			tempObj.putUtfString("Zone", this.getParentZone().getName());
			tempObj.putUtfString("Time", sdf.format(now.getTime()));
			tempObj.putInt("TotalUsers", totalUser);
			tempObj.putInt("TotalAllRooms", totalRoom);
			tempObj.putInt("TotalTournamentRoom", totalTournament);
			tempObj.putInt("TotalPrivateRoom", totalPrivate);
			tempObj.putInt("TotalVipRoom", totalVip);
			
			ISFSArray tempArray = new SFSArray();
			for(String host : _gameServer.keySet())
			{
				int gsTotalUser = _gameServerTotalUser.get(host);
				int gsTotalRoom = _gameServerTotalRoom.get(host);
				ISFSObject gameObj = new SFSObject();
				gameObj.putUtfString("Host", host);
				gameObj.putInt("TotalUsers", gsTotalUser);
				gameObj.putInt("TotalRooms", gsTotalRoom);
				tempArray.addSFSObject(gameObj);
			}
			tempObj.putSFSArray("GameServer", tempArray);
		}
		catch(Exception e)
		{
			tempObj.putUtfString("Error", "Unknown Server Error, please try again.");
			trace(ExtensionLogLevel.ERROR, String.format("GetServerInfoJson : %s.", ErrorClass.StackTraceToString(e)));
		}
		return tempObj.toJson();
	}

	private String ListOnlineUsersJson()
	{
		ISFSArray tempArray = new SFSArray();
		try
		{
			ArrayList<User> tempList = new ArrayList<User>(this.getParentZone().getUserList());
			for(User tempUser : tempList)
			{
				String name = tempUser.getName();
				int playerID = Integer.valueOf(name);
				if (playerID > 10)
				{
					tempArray.addInt(playerID);
				}
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("ListOnlineUsersJson : %s.", ErrorClass.StackTraceToString(e)));
		}
		return tempArray.toJson();
	}
	
	private String SetLeagueStatus(String status)
	{
		String temp = "";
		try
		{
			_leagueStatus = status.equalsIgnoreCase("LSTS0010");
			temp = String.format("League Status set to : %s, value : %s", status, _leagueStatus);
			
			ISFSObject sendData = new SFSObject();
			sendData.putUtfString(ConstantClass.ACTION, ConstantClass.MASTER_SERVER_INFORMATION);
			sendData.putBool(ConstantClass.LOG_STATUS, _showDetailLog);
			sendData.putBool(ConstantClass.LEAGUE_STATUS, _leagueStatus);
			NotifyAllGameServer(sendData);
			
			trace(temp);
		}
		catch(Exception e)
		{
			temp = "Unknown Server Error, please try again.";
			trace(ExtensionLogLevel.ERROR, String.format("SetLeagueStatus : %s.", ErrorClass.StackTraceToString(e)));
		}
		return temp;
	}
	
	/*
	 * Jackpot
	*/
	private String UpdateJackpot(String value)
	{
		String temp = "";
		try
		{
			UpdateAndSaveJackpot(Long.valueOf(value));
			temp = String.format("Jackpot updated, new value : %s", _jackpot);
		}
		catch(Exception e)
		{
			temp = "Unknown Server Error, please try again.";
			trace(ExtensionLogLevel.ERROR, String.format("UpdateJackpot : %s.", ErrorClass.StackTraceToString(e)));
		}
		return temp;
	}
	
	private void JackpotWins(ISFSObject data)
	{
		try
		{
			long winAmount = data.getLong(ConstantClass.JACKPOT_TOTAL);
			long minus = data.getLong(ConstantClass.JACKPOT_MINUS);
			String type = data.getUtfString(ConstantClass.JACKPOT_TYPE);
			String nickname = data.getUtfString(ConstantClass.NICKNAME);
			int id = data.getInt(ConstantClass.USER_ID);
			
			UpdateAndSaveJackpot(minus * -1);
			
			
			ArrayList<ISession> sessionList = (ArrayList<ISession>) this.getParentZone().getSessionList();
			if (sessionList.size() > 0)
			{
				ISFSObject sendObj = new SFSObject();
				sendObj.putUtfString(ConstantClass.NICKNAME, nickname);
				sendObj.putInt(ConstantClass.USER_ID, id);
				sendObj.putLong(ConstantClass.JACKPOT_TOTAL, winAmount);
				this.getApi().sendAdminMessage(null, "WinJackpot", sendObj, sessionList);
			}
			
			DBManager.getInstance().InsertJackpotWinner(id, type, winAmount);
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("JackpotWins : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void UpdateAndSaveJackpot(long add)
	{
		try
		{
			long cek = _jackpot.addAndGet(add);
			if (cek < 100000000)
			{
				_jackpot.set(100000000L);
			}
			if (cek > 9999999999L)
			{
				_jackpot.set(9999999999L);
			}
			if (_outputstream == null)
			{
				_outputstream = new FileOutputStream("jackpot.cfg");
			}
			Properties testprop = new Properties();
			testprop.setProperty("jackpot", String.valueOf(_jackpot));
			testprop.store(_outputstream, null);
		}
		catch(FileNotFoundException e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("Save Jackpot Error : %s.", ErrorClass.StackTraceToString(e)));
		}
		catch(SecurityException s)
		{
			trace(ExtensionLogLevel.ERROR, String.format("Save Jackpot Error : %s.", ErrorClass.StackTraceToString(s)));
		}
		catch(Exception a)
		{
			trace(ExtensionLogLevel.ERROR, String.format("Save Jackpot Error : %s.", ErrorClass.StackTraceToString(a)));
		}
	}
	
	
	
	
	
	
	
	
	
	/*
	 * Push Event
	 */
	
    public boolean IsPushEvent()
	{
		if (_pushEventState == 2)
		{
			return true;
		}
		return false;
	}
	
	public void AddPushEventLoginTarget(int userID)
	{
		try
		{
			int targetType = _pushEventData.get(_pushEventCurrent).getInt("TargetType");
			if (targetType == 0)
			{
				_listPushGiftLogin.add(userID);
			}
			else if (targetType == 1)
			{
				SusunUserData data = userData.get(userID);
				if (data != null)
				{
					if(!data.guest)
					{
						_listPushGiftLogin.add(userID);
					}
				}
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("AddPushEventLoginTarget : %s.", ErrorClass.StackTraceToString(e)));
		}	
	}
	
	public void ClearPushEventData() 
	{
		try
		{
			if (_pushEventSchedule != null)
			{
				_pushEventSchedule.cancel(true);
				_pushEventSchedule = null;
			}
			if (_loginPushSchedule != null)
			{
				_loginPushSchedule.cancel(true);
				_loginPushSchedule = null;
			}
			_pushEventData.clear();
			if (_listPushGiftOnline != null)
			{
				_listPushGiftOnline.clear();
				_listPushGiftOnline = null;
			}
			_listPushGiftLogin.clear();
			_listPushDoneGift.clear();
			_pushEventState = 0;
			_pushEventCurrent = -1;
		}
		catch(Exception e)
		{
			_pushEventState = 0;
			trace(ExtensionLogLevel.ERROR, String.format("ClearPushEventData : %s.", ErrorClass.StackTraceToString(e)));
		}	
	}
	
	public void LoadPushEventData() throws Exception
	{
		if (!_isLoadPushEvent)
		{
			_isLoadPushEvent = true;
			SmartFoxServer sfs = SmartFoxServer.getInstance();
			sfs.getTaskScheduler().schedule(new GetPushEvent(), 50, TimeUnit.MILLISECONDS);
		}
	}
	
	public void SendPushEventGiftToUser(int userID) throws Exception
	{
		if (_listPushDoneGift.contains(userID))
		{
			return;
		}
		if (_listPushDoneGift.add(userID))
		{
			DBManager dm = DBManager.getInstance();
			String rewardMessage = _pushEventData.get(_pushEventCurrent).getUtfString("RewardMessage");
			ISFSObject decodeRewards = _pushEventData.get(_pushEventCurrent).getSFSObject("Rewards");
			int totalReward = decodeRewards.getSFSArray("data").size();
	    	for(int j = 0; j < totalReward; j++)
			{
	    		int rewardType = decodeRewards.getSFSArray("data").getSFSObject(j).getInt("RewardType");
				int rewardValue = decodeRewards.getSFSArray("data").getSFSObject(j).getInt("RewardValue"); 
				String extraMessage = String.format("{\"Type\":80,\"ID\":0,\"Message\":\"%s\"}", rewardMessage);
				int retIns = dm.InsertUserGift(userID, 0, rewardType, rewardValue, extraMessage);
				if(retIns != 0)
				{
					trace(ExtensionLogLevel.ERROR, "SendPushEventGiftToUser : InsertUserGift ERROR.");
				}
			}
		}
	}
	
	public String DeletePushEvent(ISFSObject data) throws Exception
	{
		String result = "";
		String eventName = data.getUtfString("name");
		boolean isForce = data.getBool("force");
		try
		{
			
			int findIndex = -1;
			int totalEvent = _pushEventData.size();
			for(int i = 0; i < totalEvent; i++)
			{
				if (eventName.equals(_pushEventData.get(i).getUtfString("EventName")))
				{
					findIndex = i;
					break;
				}
			}
			if (findIndex == -1)
			{
				result = String.format("Push Event : %s not found.", eventName);
				return result;
			}
			if (findIndex == _pushEventCurrent)
			{
				if (_pushEventState != 2)
				{
					_pushEventState = 0;
					_pushEventSchedule.cancel(true);
					_pushEventSchedule = null;
					_pushEventData.get(findIndex).putBool("IsFinished", true);
					result = String.format("Push Event : %s Canceled.", eventName);
					trace(ExtensionLogLevel.INFO, result);
				}
				else
				{
					if(isForce)
					{
						_pushEventState = 0;
						_pushEventSchedule.cancel(true);
						_pushEventSchedule = null;
						_loginPushSchedule.cancel(true);
						_loginPushSchedule = null;
						if (_listPushGiftOnline != null)
						{
							_listPushGiftOnline.clear();
							_listPushGiftOnline = null;
						}
						_listPushGiftLogin.clear();
						_listPushDoneGift.clear();
						_pushEventData.get(findIndex).putBool("IsFinished", true);
						result = String.format("Push Event : %s Ended.", eventName);
						trace(ExtensionLogLevel.INFO, result);
					}
					else
					{
						result = String.format("Push Event : Unable Delete %s since it is running.", eventName);
					}
				}
				// Find next Push Event
				if (_pushEventState == 0)
				{
					boolean foundEvent = false;
					int tempIndex = _pushEventCurrent + 1; 
					while (!foundEvent && (tempIndex < totalEvent))
					{
						if(!_pushEventData.get(tempIndex).getBool("IsFinished"))
						{
							String nextName = _pushEventData.get(tempIndex).getUtfString("EventName");
							String startTime = _pushEventData.get(tempIndex).getUtfString("StartTime");
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							Calendar next = Calendar.getInstance();
							next.setTime(sdf.parse(startTime));
							int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
							if (delay >= 0)
							{
								if (delay == 0) 
								{
									delay = 1;
								}
								_pushEventState = 1;
								_pushEventCurrent = tempIndex;
								SmartFoxServer sfs = SmartFoxServer.getInstance();
								_pushEventSchedule = sfs.getTaskScheduler().schedule(new StartPushEvent(), delay, TimeUnit.SECONDS);
								trace(ExtensionLogLevel.INFO, String.format("Push Event : %s will be started at %s, in %d Seconds.", nextName, startTime, delay));
								foundEvent = true;
							}
							else
							{
								_pushEventData.get(tempIndex).putBool("IsFinished", true);
								trace(ExtensionLogLevel.WARN, String.format("Push Event : %s already pass the time.", nextName));
								tempIndex++;
							}
						}
						else
						{
							tempIndex++;
						}
					}
				}
			}
			else
			{
				_pushEventData.get(findIndex).putBool("IsFinished", true);
				result = String.format("Push Event : %s Deleted.", eventName);
				trace(ExtensionLogLevel.INFO, result);
			}
		}
		catch(Exception e)
		{
			result = String.format("Failed To delete %s, Server Unknown Error.", eventName);
			trace(ExtensionLogLevel.ERROR, String.format("DeletePushEvent : %s.", ErrorClass.StackTraceToString(e)));
		}
		return result;
	}

	private String ListPushEventJson()
	{
		ISFSArray tempArray = new SFSArray();
		try
		{
			int totalEvent = _pushEventData.size();
			
			for(int i = 0; i < totalEvent; i++)
			{
				if(!_pushEventData.get(i).getBool("IsFinished"))
				{
					tempArray.addSFSObject(_pushEventData.get(i));
				}
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("ListPushEventJson : %s.", ErrorClass.StackTraceToString(e)));
		}
		return tempArray.toJson();
	}
	
 	private class GetPushEvent implements Runnable
	{
		public void run()
		{
			_isLoadPushEvent = false;
			try
			{
				// Reset Push Event
				ClearPushEventData();
				// Load Push Event Data from Database
				DBManager dm = DBManager.getInstance();
				List<Object[]> resList1 =  dm.GetPushEvent();
				for(Object[] obj : resList1)
				{
					String eventName = obj[0].toString();
					int targetType = Integer.parseInt(obj[1].toString());
					String startTime = obj[2].toString();
					String endTime = obj[3].toString();
					String rewards = obj[4].toString();
					String rewardMessage = obj[5].toString();
					String noticeMessage = obj[6].toString();
					ISFSObject decodeRewards = SFSObject.newFromJsonData(String.format("{data:%s}", rewards));
					ISFSObject tempObj = new SFSObject();
					tempObj.putInt("TargetType", targetType);
					tempObj.putUtfString("EventName", eventName);
					tempObj.putUtfString("StartTime", startTime);
					tempObj.putUtfString("EndTime", endTime);
					tempObj.putUtfString("RewardMessage", rewardMessage);
					tempObj.putUtfString("NoticeMessage", noticeMessage);
					tempObj.putUtfString("RewardString", rewards);
					tempObj.putSFSObject("Rewards", decodeRewards);
					tempObj.putBool("IsFinished", false);
					_pushEventData.add(tempObj);
				}
				int totalEvent = _pushEventData.size();
				if (totalEvent > 0)
				{
					boolean foundEvent = false;
					int tempIndex = 0;
					while (!foundEvent && (tempIndex < totalEvent))
					{
						String eventName = _pushEventData.get(tempIndex).getUtfString("EventName");
						String startTime = _pushEventData.get(tempIndex).getUtfString("StartTime");
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Calendar next = Calendar.getInstance();
						next.setTime(sdf.parse(startTime));
						int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
						if (delay >= 0)
						{
							if (delay == 0) 
							{
								delay = 1;
							}
							_pushEventState = 1;
							_pushEventCurrent = tempIndex;
							SmartFoxServer sfs = SmartFoxServer.getInstance();
							_pushEventSchedule = sfs.getTaskScheduler().schedule(new StartPushEvent(), delay, TimeUnit.SECONDS);
							trace(ExtensionLogLevel.INFO, String.format("Push Event : %s will be started at %s, in %d Seconds", eventName, startTime, delay));
							foundEvent = true;
						}
						else
						{
							_pushEventData.get(tempIndex).putBool("IsFinished", true);
							trace(ExtensionLogLevel.WARN, String.format("Push Event : %s already pass the time", eventName));
							tempIndex++;
						}
					}
				}
			}
			catch(Exception e)
			{
				_pushEventState = 0;
				trace(ExtensionLogLevel.ERROR, String.format("GetPushEvent : %s.", ErrorClass.StackTraceToString(e)));
			}	
		}
	}
 	
	private class StartPushEvent implements Runnable
	{
		public void run()
		{
			try
			{
				String eventName = _pushEventData.get(_pushEventCurrent).getUtfString("EventName");
				int targetType = _pushEventData.get(_pushEventCurrent).getInt("TargetType");
				if (targetType == 0)
				{
					_listPushGiftOnline = new ArrayList<Integer>(userData.keySet());
				}
				else if (targetType == 1)
				{
					_listPushGiftOnline = new ArrayList<Integer>();
					ArrayList<Integer> tempSet = new ArrayList<Integer>(userData.keySet());
					for (int userId : tempSet)
					{
						SusunUserData data = userData.get(userId);
						if (data != null)
						{
							if(!data.guest)
							{
								_listPushGiftOnline.add(userId);
							}
						}
					}
				}
				_pushEventState = 2;
				SmartFoxServer sfs = SmartFoxServer.getInstance();
				_pushEventSchedule = sfs.getTaskScheduler().scheduleAtFixedRate(new GiftPushEvent(), 10, 10, TimeUnit.MILLISECONDS);
				_loginPushSchedule = sfs.getTaskScheduler().scheduleAtFixedRate(new LoginPushEvent(), 40, 25, TimeUnit.MILLISECONDS);
				trace(ExtensionLogLevel.INFO, String.format("Push Event : %s Start.", eventName));
			}
			catch(Exception e)
			{
				ClearPushEventData();
				trace(ExtensionLogLevel.ERROR, String.format("StartPushEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class GiftPushEvent implements Runnable
	{
		public void run()
		{
			try
			{
				if (_listPushGiftOnline.size() > 0)
				{
					int userID = _listPushGiftOnline.remove(0);
					SendPushEventGiftToUser(userID);
				}
				if (_listPushGiftOnline.isEmpty())
				{
					_listPushGiftOnline = null;
					_pushEventSchedule.cancel(true);
					_pushEventSchedule = null;
					String eventName = _pushEventData.get(_pushEventCurrent).getUtfString("EventName");
					String endTime = _pushEventData.get(_pushEventCurrent).getUtfString("EndTime");
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					Calendar next = Calendar.getInstance();
					next.setTime(sdf.parse(endTime));
					int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
					if (delay < 0)
					{
						delay = 1;
					}
					SmartFoxServer sfs = SmartFoxServer.getInstance();
					_pushEventSchedule = sfs.getTaskScheduler().schedule(new EndPushEvent(), delay, TimeUnit.SECONDS);
					trace(ExtensionLogLevel.INFO, String.format("Push Event : %s, Gift Online User Done, Now wait for End time.", eventName));
				}
			}
			catch(Exception e)
			{
				ClearPushEventData();
				trace(ExtensionLogLevel.ERROR, String.format("GiftPushEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class LoginPushEvent implements Runnable
	{
		public void run()
		{
			try
			{
				if (_listPushGiftLogin.size() > 0)
				{
					int userID = _listPushGiftLogin.remove(0);
					SendPushEventGiftToUser(userID);
				}
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("LoginPushEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class EndPushEvent implements Runnable
	{
		public void run()
		{
			try
			{
				_pushEventState = 0;
				_pushEventSchedule.cancel(true);
				_pushEventSchedule = null;
				_loginPushSchedule.cancel(true);
				_loginPushSchedule = null;
				_listPushGiftLogin.clear();
				_listPushDoneGift.clear();
				String finishEvent = _pushEventData.get(_pushEventCurrent).getUtfString("EventName");
				_pushEventData.get(_pushEventCurrent).putBool("IsFinished", true);
				trace(ExtensionLogLevel.INFO, String.format("Push Event : %s Ended.", finishEvent));
				
				boolean foundEvent = false;
				int tempIndex = _pushEventCurrent + 1; 
				int totalEvent = _pushEventData.size();
				while (!foundEvent && (tempIndex < totalEvent))
				{
					if(!_pushEventData.get(tempIndex).getBool("IsFinished"))
					{
						String eventName = _pushEventData.get(tempIndex).getUtfString("EventName");
						String startTime = _pushEventData.get(tempIndex).getUtfString("StartTime");
						
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Calendar next = Calendar.getInstance();
						next.setTime(sdf.parse(startTime));
						int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
						if (delay >= 0)
						{
							if (delay == 0) 
							{
								delay = 1;
							}
							_pushEventState = 1;
							_pushEventCurrent = tempIndex;
							SmartFoxServer sfs = SmartFoxServer.getInstance();
							_pushEventSchedule = sfs.getTaskScheduler().schedule(new StartPushEvent(), delay, TimeUnit.SECONDS);
							trace(ExtensionLogLevel.INFO, String.format("Push Event : %s will be started at %s, in %d Seconds", eventName, startTime, delay));
							foundEvent = true;
						}
						else
						{
							_pushEventData.get(tempIndex).putBool("IsFinished", true);
							trace(ExtensionLogLevel.WARN, String.format("Push Event : %s already pass the time", eventName));
							tempIndex++;
						}
					}
					else
					{
						tempIndex++;
					}
				}
			}
			catch(Exception e)
			{
				ClearPushEventData();
				trace(ExtensionLogLevel.ERROR, String.format("EndPushEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}

	/*
	 * Hot Time Event
	 */
	
	public void LoadHotTimeEventData() throws Exception
	{
		if (!_isLoadHotTimeEvent)
		{
			_isLoadHotTimeEvent = true;
			SmartFoxServer sfs = SmartFoxServer.getInstance();
			sfs.getTaskScheduler().schedule(new GetHotTimeEvent(), 50, TimeUnit.MILLISECONDS);
		}
	}
		
	public void ClearHotTimeEventData()
	{
		try
		{
			if (_hotTimeEventSchedule != null)
			{
				_hotTimeEventSchedule.cancel(true);
				_hotTimeEventSchedule = null;
			}
			_hotTimeEventData.clear();
			_hotTimeEventState = 0;
			_hotTimeEventCurrent = -1;
			ISFSObject sendObj = new SFSObject();
			sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_HOT_TIME_EVENT);
			sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, false);
			NotifyAllGameServer(sendObj);
		}
		catch(Exception e)
		{
			_hotTimeEventState = 0;
			trace(ExtensionLogLevel.ERROR, String.format("ClearHotTimeEventData : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public ISFSObject GetHotTimeInfo()
	{
		ISFSObject data = new SFSObject();
		boolean haveHotTime = false;
		try
		{
			if (_hotTimeEventState == 2)
			{
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String endTime = _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("EndTime");
				Calendar endCal = Calendar.getInstance();
				endCal.setTime(sdf.parse(endTime));
				long curMilis = System.currentTimeMillis();
				long endMilis = endCal.getTimeInMillis();
				long timeLeft = TimeUnit.MILLISECONDS.toSeconds(endMilis - curMilis);
				if (timeLeft > 0)
				{
					haveHotTime = true;
					data.putBool(ConstantClass.IS_HOT_TIME_EVENT, true);
					data.putInt(ConstantClass.HOT_TIME_EVENT_TYPE_TARGET, _hotTimeEventData.get(_hotTimeEventCurrent).getInt("TargetType"));
					data.putInt(ConstantClass.HOT_TIME_EVENT_TAX_REDUCE, _hotTimeEventData.get(_hotTimeEventCurrent).getInt("TaxReduce"));
					data.putInt(ConstantClass.HOT_TIME_EVENT_REWARD_TIMES, _hotTimeEventData.get(_hotTimeEventCurrent).getInt("RewardTimes"));
					data.putInt(ConstantClass.HOT_TIME_MIN_MULTI, _hotTimeMinMulti);
					data.putInt(ConstantClass.HOT_TIME_MAX_MULTI, _hotTimeMaxMulti);
					data.putUtfString(ConstantClass.HOT_TIME_EVENT_START_TIME, _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("StartTime"));
					data.putUtfString(ConstantClass.HOT_TIME_EVENT_END_TIME, endTime);
					data.putLong(ConstantClass.HOT_TIME_TIMELEFT, timeLeft);
				}
			}
		}
		catch(Exception e)
		{
			haveHotTime = false;
			trace(ExtensionLogLevel.ERROR, String.format("GetHotTimeInfo : %s", ErrorClass.StackTraceToString(e)));
		}
		if(!haveHotTime)
		{
			data.putBool(ConstantClass.IS_HOT_TIME_EVENT, false);
			data.putInt(ConstantClass.HOT_TIME_EVENT_TYPE_TARGET, 0);
			data.putInt(ConstantClass.HOT_TIME_EVENT_TAX_REDUCE, 0);
			data.putInt(ConstantClass.HOT_TIME_EVENT_REWARD_TIMES, 0);
			data.putInt(ConstantClass.HOT_TIME_MIN_MULTI, 1);
			data.putInt(ConstantClass.HOT_TIME_MAX_MULTI, 1);
			data.putUtfString(ConstantClass.HOT_TIME_EVENT_START_TIME, "-");
			data.putUtfString(ConstantClass.HOT_TIME_EVENT_END_TIME, "-");
			data.putLong(ConstantClass.HOT_TIME_TIMELEFT, 0);
		}
		return data;
	}
	
	public String DeleteHotTimeEvent(ISFSObject data) throws Exception
	{
		String result = "";
		String eventName = data.getUtfString("name");
		boolean isForce = data.getBool("force");
		try
		{
			int findIndex = -1;
			int totalEvent = _hotTimeEventData.size();
			for(int i = 0; i < totalEvent; i++)
			{
				if (eventName.equals(_hotTimeEventData.get(i).getUtfString("EventName")))
				{
					findIndex = i;
					break;
				}
			}
			if (findIndex == -1)
			{
				result = String.format("Hot Time Event : %s not found.", eventName);
				return result;
			}
			if (findIndex == _hotTimeEventCurrent)
			{
				if (_hotTimeEventState < 2)
				{
					_hotTimeEventState = 0;
					_hotTimeEventSchedule.cancel(true);
					_hotTimeEventSchedule = null;
					_hotTimeEventData.get(findIndex).putBool("IsFinished", true);
					
					ISFSObject sendObj = new SFSObject();
					sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_HOT_TIME_EVENT);
					sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, false);
					NotifyAllGameServer(sendObj);
					
					result = String.format("Hot Time Event : %s Canceled.", eventName);
					trace(ExtensionLogLevel.INFO, result);
				}
				else
				{
					if(isForce)
					{
						_hotTimeEventState = 0;
						_hotTimeEventSchedule.cancel(true);
						_hotTimeEventSchedule = null;
						_hotTimeEventData.get(findIndex).putBool("IsFinished", true);
						
						ISFSObject sendObj = new SFSObject();
						sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_HOT_TIME_EVENT);
						sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, false);
						NotifyAllGameServer(sendObj);
						
						result = String.format("Hot Time Event : %s Ended.", eventName);
						trace(ExtensionLogLevel.INFO, result);
					}
					else
					{
						result = String.format("Hot Time Event : Unable Delete %s since it is running.", eventName);
					}
				}
				// Find next Hot Time Event
				if (_hotTimeEventState == 0)
				{
					boolean foundEvent = false;
					int tempIndex = _hotTimeEventCurrent + 1; 
					while (!foundEvent && (tempIndex < totalEvent))
					{
						if(!_hotTimeEventData.get(tempIndex).getBool("IsFinished"))
						{
							String nextName = _hotTimeEventData.get(tempIndex).getUtfString("EventName");
							String startTime = _hotTimeEventData.get(tempIndex).getUtfString("StartTime");
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							Calendar next = Calendar.getInstance();
							next.setTime(sdf.parse(startTime));
							int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
							if (delay >= 0)
							{
								if (delay == 0) 
								{
									delay = 1;
								}
								_hotTimeEventState = 1;
								_hotTimeEventCurrent = tempIndex;
								SmartFoxServer sfs = SmartFoxServer.getInstance();
								_hotTimeEventSchedule = sfs.getTaskScheduler().schedule(new StartHotTimeEvent(), delay, TimeUnit.SECONDS);
								trace(ExtensionLogLevel.INFO, String.format("HotTime Event : %s will be started at %s, in %d Seconds", nextName, startTime, delay));
								foundEvent = true;
							}
							else
							{
								_hotTimeEventData.get(tempIndex).putBool("IsFinished", true);
								trace(ExtensionLogLevel.WARN, String.format("HotTime Event : %s already pass the time", eventName));
								tempIndex++;
							}
						}
						else
						{
							tempIndex++;
						}
					}
				}
			}
			else
			{
				_hotTimeEventData.get(findIndex).putBool("IsFinished", true);
				result = String.format("Hot Time Event : %s Deleted.", eventName);
				trace(ExtensionLogLevel.INFO, result);
			}
		}
		catch(Exception e)
		{
			result = String.format("Failed To delete %s, Server Unknown Error.", eventName);
			trace(ExtensionLogLevel.ERROR, String.format("DeleteHotTimeEvent : %s.", ErrorClass.StackTraceToString(e)));
		}
		return result;
	}

	private String ListHotTimeEventJson()
	{
		ISFSArray tempArray = new SFSArray();
		try
		{
			int totalEvent = _hotTimeEventData.size();
			
			for(int i = 0; i < totalEvent; i++)
			{
				if(!_hotTimeEventData.get(i).getBool("IsFinished"))
				{
					tempArray.addSFSObject(_hotTimeEventData.get(i));
				}
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("ListHotTimeEventJson : %s.", ErrorClass.StackTraceToString(e)));
		}
		return tempArray.toJson();
	}
	
	private class GetHotTimeEvent implements Runnable
	{
		public void run()
		{
			_isLoadHotTimeEvent = false;
			try
			{
				// Reset Hot Time Data
				ClearHotTimeEventData();
				// Load Hot Time Data from Database
				DBManager dm = DBManager.getInstance();
				List<Object[]> resList1 =  dm.GetHotTimeEvent();
				for(Object[] obj : resList1)
				{
					String eventName = obj[0].toString();
					int targetType = Integer.parseInt(obj[1].toString());
					String startTime = obj[2].toString();
					String endTime = obj[3].toString();
					int taxReduce = Integer.parseInt(obj[4].toString());
					int rewardTimes = Integer.parseInt(obj[5].toString());
					String noticeMessage = obj[6].toString();
					ISFSObject tempObj = new SFSObject();
					tempObj.putInt("TargetType", targetType);
					tempObj.putUtfString("EventName", eventName);
					tempObj.putUtfString("StartTime", startTime);
					tempObj.putUtfString("EndTime", endTime);
					tempObj.putInt("TaxReduce", taxReduce);
					tempObj.putInt("RewardTimes", rewardTimes);
					tempObj.putUtfString("NoticeMessage", noticeMessage);
					tempObj.putBool("IsFinished", false);
					_hotTimeEventData.add(tempObj);
				}
				int totalEvent = _hotTimeEventData.size();
				if (totalEvent > 0)
				{
					int tempIndex = 0;
					String eventName = _hotTimeEventData.get(tempIndex).getUtfString("EventName");
					String startTime = _hotTimeEventData.get(tempIndex).getUtfString("StartTime");
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					Calendar next = Calendar.getInstance();
					next.setTime(sdf.parse(startTime));
					int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
					// Maybe Hot Time already Started but not ended when Hot Time Reload...
					// 30 seconds min delay for GameServer to connected to master
					if (delay < 30)
					{
						delay = 30;
					}
					
					_hotTimeEventState = 1;
					_hotTimeEventCurrent = tempIndex;
					SmartFoxServer sfs = SmartFoxServer.getInstance();
					_hotTimeEventSchedule = sfs.getTaskScheduler().schedule(new StartHotTimeEvent(), delay, TimeUnit.SECONDS);
					trace(ExtensionLogLevel.INFO, String.format("HotTime Event : %s will be started at %s, in %d Seconds", eventName, startTime, delay));
				}
			}
			catch(Exception e)
			{
				_hotTimeEventState = 0;
				trace(ExtensionLogLevel.ERROR, String.format("GetHotTimeEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class StartHotTimeEvent implements Runnable
	{
		public void run()
		{
			try
			{
				String eventName = _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("EventName");
				String endTime = _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("EndTime");
				_hotTimeEventState = 2;
				
				ISFSObject sendObj = new SFSObject();
				sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_HOT_TIME_EVENT);
				sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, true);
				sendObj.putUtfString(ConstantClass.HOT_TIME_EVENT_START_TIME, _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("StartTime"));
				sendObj.putUtfString(ConstantClass.HOT_TIME_EVENT_END_TIME, _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("EndTime"));
				sendObj.putInt(ConstantClass.HOT_TIME_EVENT_TAX_REDUCE, _hotTimeEventData.get(_hotTimeEventCurrent).getInt("TaxReduce"));
				sendObj.putInt(ConstantClass.HOT_TIME_EVENT_REWARD_TIMES,  _hotTimeEventData.get(_hotTimeEventCurrent).getInt("RewardTimes"));
				sendObj.putInt(ConstantClass.HOT_TIME_EVENT_TYPE_TARGET,  _hotTimeEventData.get(_hotTimeEventCurrent).getInt("TargetType"));
				NotifyAllGameServer(sendObj);
				
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Calendar next = Calendar.getInstance();
				next.setTime(sdf.parse(endTime));
				int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
				if (delay < 5)
				{
					delay = 5;
				}
				SmartFoxServer sfs = SmartFoxServer.getInstance();
				_hotTimeEventSchedule = sfs.getTaskScheduler().schedule(new EndHotTimeEvent(), delay, TimeUnit.SECONDS);
				trace(ExtensionLogLevel.INFO, String.format("HotTime Event : %s Started, Now wait for End time.", eventName));
			}
			catch(Exception e)
			{
				ClearHotTimeEventData();
				trace(ExtensionLogLevel.ERROR, String.format("StartHotTimeEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class EndHotTimeEvent implements Runnable
	{
		public void run()
		{
			try
			{
				_hotTimeEventState = 0;
				String finishEvent = _hotTimeEventData.get(_hotTimeEventCurrent).getUtfString("EventName");
				_hotTimeEventData.get(_hotTimeEventCurrent).putBool("IsFinished", true);
				trace(ExtensionLogLevel.INFO, String.format("Hot Time Event : %s Ended.", finishEvent));
				
				ISFSObject sendObj = new SFSObject();
				sendObj.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_HOT_TIME_EVENT);
				sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, false);
				NotifyAllGameServer(sendObj);
				
				boolean foundEvent = false;
				int tempIndex = _hotTimeEventCurrent + 1; 
				int totalEvent = _hotTimeEventData.size();
				while (!foundEvent && (tempIndex < totalEvent))
				{
					if(!_hotTimeEventData.get(tempIndex).getBool("IsFinished"))
					{
						String eventName = _hotTimeEventData.get(tempIndex).getUtfString("EventName");
						String startTime = _hotTimeEventData.get(tempIndex).getUtfString("StartTime");
						
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Calendar next = Calendar.getInstance();
						next.setTime(sdf.parse(startTime));
						int delay = (int)TimeUnit.MILLISECONDS.toSeconds(next.getTimeInMillis() - System.currentTimeMillis());
						if (delay >= 0)
						{
							if (delay == 0) 
							{
								delay = 1;
							}
							_hotTimeEventState = 1;
							_hotTimeEventCurrent = tempIndex;
							SmartFoxServer sfs = SmartFoxServer.getInstance();
							_hotTimeEventSchedule = sfs.getTaskScheduler().schedule(new StartHotTimeEvent(), delay, TimeUnit.SECONDS);
							trace(ExtensionLogLevel.INFO, String.format("HotTime Event : %s will be started at %s, in %d Seconds", eventName, startTime, delay));
							foundEvent = true;
						}
						else
						{
							_hotTimeEventData.get(tempIndex).putBool("IsFinished", true);
							trace(ExtensionLogLevel.WARN, String.format("HotTime Event : %s already pass the time", eventName));
							tempIndex++;
						}
					}
					else
					{
						tempIndex++;
					}
				}
			}
			catch(Exception e)
			{
				ClearHotTimeEventData();
				trace(ExtensionLogLevel.ERROR, String.format("EndHotTimeEvent : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
}
