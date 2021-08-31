package mbs.diamond.GameExtension;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.api.CreateRoomSettings;
import com.smartfoxserver.v2.api.CreateRoomSettings.RoomExtensionSettings;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.db.DBConfig;
import com.smartfoxserver.v2.db.IDBManager;
import com.smartfoxserver.v2.db.SFSDBManager;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.SFSRoomSettings;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.ExtensionLogLevel;
import com.smartfoxserver.v2.extensions.SFSExtension;

import mbs.diamond.capsasusun.ConstantClass;
import mbs.diamond.capsasusun.DBManager;
import mbs.diamond.capsasusun.ErrorClass;
import mbs.diamond.capsasusun.LeagueManager;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class InGameExtension extends SFSExtension
{
	// Server Info Variables
	private final String version = "2.0.5";
	private final int _nettyPort = 4340;
	
	private int _maxUserLimit = 100;
	private int _lastTotalUser = -1;
	private int _lastTotalRoom = -1;
	
	private String _masterServerHost = "";
	private String _myHostname = "";
	
	// Netty Variables
	ClientBootstrap _clientBootstrap = null;
	ChannelFuture _clientChannelFuture = null;
	Channel _clientChannel = null;
	private boolean _isMasterConnected = false;
	private boolean _isMasterReconnect = false;
	ScheduledFuture<?> nettyReconnectHandler;
	
	// Server Variables
	private boolean _showDetailLog = false;
	private boolean _isLeagueActive = true;
	private boolean _isHotTimeEvent = false;
	private ISFSObject _hotTimeData = null;
	private ArrayList<String> _roomRemoveList = new ArrayList<String>(); 
	private ConcurrentHashMap<Integer, Integer> _decoData = new ConcurrentHashMap<Integer, Integer>();
	
	// Task Scheduler Variables
	private int coundownInfoToMaster = 3;
	private int counterInfoToMaster = coundownInfoToMaster;
	private int cowndownCleanRoom = 180;
	private int counterCleanRoom = cowndownCleanRoom;
	private int counterMasterConnect = 3;
	ScheduledFuture<?> serverCycleHandler;
	
	// Main Database
	private IDBManager _mainDbManager = null;
	private DBConfig _mainConfig = null;
		
	// Log Database
	private IDBManager _logDbManager = null;
	private DBConfig _logConfig = null;

	// Portal Database
	private IDBManager _portalDbManager = null;
	private DBConfig _portalConfig = null;
	
	JedisPool jedisPool = null;
	
	private long savedJackpot = 0L;
	
	/*
	 * Extension
	 */
	
	@Override
	public void init()
	{
		trace(ExtensionLogLevel.INFO, String.format("CapsaSusun InGameExtension v%s Initialize.", version));
		// Event Listener
		addEventHandler(SFSEventType.SERVER_READY, ServerReadyEventHandler.class);
		addEventHandler(SFSEventType.USER_LOGIN, UserLoginEventHandler.class);
		// Request Handler
		addRequestHandler(ConstantClass.KEEP_ALIVE, UserKeepAliveHandler.class);
		addRequestHandler(ConstantClass.JOIN_ROOM, RoomAskJoinHandler.class);
		addRequestHandler(ConstantClass.GET_TIMESTAMP, GetTimeStampHandler.class);
		
		LoadConfig();
		DBManager.getInstance().SetLogger(this.getLogger());
    	DBManager.getInstance().setMainDBManager(_mainDbManager);
    	DBManager.getInstance().setLogDBManager(_logDbManager);
    	DBManager.getInstance().setPortalDbManager(_portalDbManager);
    	DBManager.getInstance().setRanklDbManager(jedisPool);
    	LeagueManager.getInstance().SetManager(jedisPool, this.getLogger());
	}
	
	@Override
	public void destroy() 
	{
		trace(ExtensionLogLevel.INFO, "CapsaSusun InGameExtension Destroyed.");
		if (serverCycleHandler != null)
		{
			serverCycleHandler.cancel(true);
		}
		super.destroy();
	}

	@Override
	public Object handleInternalMessage(String cmdName, Object params)
	{
		Object returnObj = null;
		try
		{
			switch (cmdName)
			{
				// Extension
				case "ServerReady":
					ServerReady();
					break;
				case "NettyMessageReceived":
					NettyMessageReceived((MessageEvent)params);
					break;
				case "NettyChannelDisconnected":
					NettyClientDisconnect();
					break;
				// Common
				case "GetDetailLog":
					returnObj = _showDetailLog;
					break;
				case "GetDecoPrice":
					returnObj = GetDecoPrice((int)params);
					break;
				case "IsLeagueActive":
					returnObj = _isLeagueActive;
					break;
				// Hot Time
				case "IsHotTimeEvent":
					returnObj = _isHotTimeEvent;
					break;
				case "GetHotTimeEventData":
					returnObj = _hotTimeData;
					break;
				// Jackpot
				case "GetJackpot":
					returnObj = savedJackpot;
					break;
				case "AddJackpot":
					AddJackpotToMaster((long)params);
					break;
				// Rooms
				case "UserAskJoinRoom":
					UserAskJoinRoom((SFSObject)params);
					break;
				case "DeleteRoomNoConfig":
					DeleteRoomNoConfig((SFSObject)params);
					break;
				case "DeleteRoom":
					DeleteRoom((SFSObject)params);
					break;
				default:
					{
						ISFSObject sendObject = (SFSObject)params;
						sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
						sendObject.putUtfString(ConstantClass.ACTION, cmdName);
						WriteToMasterServer(sendObject.toJson());
						//trace(sendObject.toJson());
					}
					break;
			}
		}
		catch(Exception e)
		{
			returnObj = null;
			trace(ExtensionLogLevel.ERROR, String.format("handleInternalMessage : %s.", ErrorClass.StackTraceToString(e)));
		}
		return returnObj;
	}
	
	private void LoadConfig()
	{
		Properties properties = this.getConfigProperties();
		_masterServerHost 	= properties.getProperty("masterServerHost");
		_myHostname 		= properties.getProperty("myHostname");
		_maxUserLimit 		= Integer.valueOf(properties.getProperty("maxuser"));
		// Setup Main Dabatase Manager
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
		// Setup Log Dabatase Manager
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
				jedisPool = new JedisPool(new JedisPoolConfig(), rankRedisAddr,6379,1000,null,0);
	        }
	        else
	        {
	        	jedisPool = new JedisPool(new JedisPoolConfig(), rankRedisAddr,6379,1000,"mbadmin#@!123",0);
	        }
		}
		trace(ExtensionLogLevel.INFO, "CapsaSusun GameExtension Config Loaded.");
	}
	
	/*
	 * Event Callback
	 */
	
	private void ServerReady()
	{
        try
        {
    		LoadDecoData();
    		serverCycleHandler = SmartFoxServer.getInstance().getTaskScheduler().scheduleAtFixedRate(new GameServerCycle(), 1, 1, TimeUnit.SECONDS);		
    		
    		trace(ExtensionLogLevel.INFO, "CapsaSusun GameExtension Server Ready.");
        }
        catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("ServerReady : %s.", ErrorClass.StackTraceToString(e)));
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
				case ConstantClass.MASTER_SERVER_INFORMATION:
					GetMasterServerInformation(requestObj);
					break;
				case ConstantClass.UPDATE_HOT_TIME_EVENT:
					UpdateHotTimeEvent(requestObj);
					break;
				case ConstantClass.GAME_SERVER_CREATE_ROOM:
					CreateRoom(requestObj);
					break;
				case ConstantClass.GAME_SERVER_CHECK_ROOM_EXIST:
					CheckRoomExist(requestObj.getUtfString(ConstantClass.ROOM_LIST));
					break;
				case ConstantClass.GAME_SERVER_RESYNC_ROOM:
					MasterAskRoomResync(requestObj.getInt(ConstantClass.ROOM_KEY));
					break;
				case ConstantClass.UPDATE_JACKPOT:
					savedJackpot = requestObj.getLong(ConstantClass.JACKPOT);
					break;
					
					
					
				default:
					trace(ExtensionLogLevel.WARN, String.format("Unknown Netty Message received : %s", e.getMessage()));
					break;		
			}
		}
		catch(Exception ex)
		{
			trace(ExtensionLogLevel.ERROR, String.format("NettyClientHandler messageReceived : %s.\nInput Message : %s.", ErrorClass.StackTraceToString(ex), incomingMessage));
		}
	}
	
	/*
	 * Server Function
	 */
	
	private void AddJackpotToMaster(long add) throws Exception
	{
		ISFSObject sendObject = new SFSObject();
		sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.UPDATE_JACKPOT);
		sendObject.putLong(ConstantClass.JACKPOT, add);
    	WriteToMasterServer(sendObject.toJson());
	}
	
	private void GetMasterServerInformation(ISFSObject data) throws Exception
	{
		boolean masterDetailLog = data.getBool(ConstantClass.LOG_STATUS);
		if (_showDetailLog !=  masterDetailLog)
		{
			_showDetailLog = masterDetailLog;
			trace(String.format("Show Detail Log set to : %s.", _showDetailLog));
		}
		boolean masterLeagueStatus = data.getBool(ConstantClass.LEAGUE_STATUS);
		if (_isLeagueActive != masterLeagueStatus)
		{
			_isLeagueActive = masterLeagueStatus;
			trace(String.format("League Status set to : %s.", _isLeagueActive));
		}
	}
	
	private void WriteToMasterServer(String message)
	{
		if (_isMasterConnected)
		{
			_clientChannel.write(message + "\r\n");
			LogMessage(String.format("WriteToMasterServer : %s", message));
		}
		else
		{
			trace(ExtensionLogLevel.ERROR, String.format("WriteToMasterServer : Master is not connected, Message : %s",  message));
		}
	}
	
	private void NettyClientDisconnect()
	{
		if (_isMasterConnected)
		{
			_isMasterConnected = false;
			_isMasterReconnect = true;
			counterMasterConnect = 3;
			trace(ExtensionLogLevel.INFO, "Disconnected from Master Server.");
		}
		else
		{
			trace(ExtensionLogLevel.ERROR, "NettyClientDisconnect : Master is not connected, but disconnect happened.");
		}
	}
	
	
	/*
	 * Room Functions
	 */
	
	private void CreateRoom(ISFSObject requestObj) throws Exception
	{
		int roomKey = requestObj.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = requestObj.getBool(ConstantClass.IS_TOURNAMENT);
		try
		{
			String roomName = Integer.toString(roomKey);
			CreateRoomSettings cfg = new CreateRoomSettings();
			cfg.setDynamic(false);
			if (isTournament)
			{
				cfg.setExtension(new RoomExtensionSettings("SusunGameExtension","mbs.diamond.GameExtension.TournamentExtension"));
			}
			else
			{
				cfg.setExtension(new RoomExtensionSettings("SusunGameExtension","mbs.diamond.GameExtension.InRoomExtension"));
			}
			cfg.setName(roomName);
			cfg.setMaxUsers(7);
			Room newRoom = this.getApi().createRoom(this.getParentZone(), cfg, null);
			newRoom.setFlag(SFSRoomSettings.CAPACITY_CHANGE, false);
			newRoom.setFlag(SFSRoomSettings.PASSWORD_STATE_CHANGE, false);
			newRoom.setFlag(SFSRoomSettings.PUBLIC_MESSAGES, false);
			newRoom.setFlag(SFSRoomSettings.ROOM_NAME_CHANGE, false);
			newRoom.setFlag(SFSRoomSettings.CAPACITY_CHANGE, false);
			newRoom.getExtension().handleInternalMessage("RoomSetup", requestObj);
		}
		catch(Exception e)
		{
			ISFSObject sendObject = new SFSObject();
        	sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CREATE_ROOM_FAILED);
        	sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
        	sendObject.putBool(ConstantClass.IS_TOURNAMENT, isTournament);
        	sendObject.putInt(ConstantClass.ROOM_KEY, roomKey);
        	WriteToMasterServer(sendObject.toJson());
			trace(ExtensionLogLevel.ERROR, String.format("CreateRoom : Failed To Create Room, Room Data : %s. Reason %s.",requestObj.toJson(), ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void UserAskJoinRoom(ISFSObject data) throws Exception
	{
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User user = this.getParentZone().getUserByName(userName);
		if (user == null)
		{
			trace(ExtensionLogLevel.ERROR, String.format("UserAskJoinRoom : User %s not found.", userName));
			return;
		}
		try
		{
			
			if ( data.containsKey(ConstantClass.ROOM_KEY))
			{
				int roomKey = data.getInt(ConstantClass.ROOM_KEY);
				String roomName = Integer.toString(roomKey);
				Room room = this.getParentZone().getRoomByName(roomName);
				if (room != null)
				{
					this.getApi().joinRoom(user, room);
				}
				else
				{
					SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.ROOM_NOT_AVAILABLE, user);
					AlertMasterRoomIsGone(roomName);
				}
			}
			else
			{
				SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.DATA_MISMATCH, user);
				
			}
		}
		catch (Exception e)
		{
			SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.SERVER_UNKNOWN_ERROR, user);
			trace(ExtensionLogLevel.ERROR, String.format("UserAskJoinRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void DeleteRoomNoConfig(ISFSObject data) throws Exception
	{
		int roomKey = data.getInt(ConstantClass.ROOM_KEY);
		boolean isTournament = data.getBool(ConstantClass.IS_TOURNAMENT);
		ISFSObject sendObject = new SFSObject();
    	sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CREATE_ROOM_FAILED);
    	sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
    	sendObject.putBool(ConstantClass.IS_TOURNAMENT, isTournament);
    	sendObject.putInt(ConstantClass.ROOM_KEY, roomKey);
    	WriteToMasterServer(sendObject.toJson());
    	trace(ExtensionLogLevel.ERROR, String.format("DeleteRoomNoConfig : RoomKey %d. Is Tournament %s", roomKey, isTournament));
	}
	
	private void DeleteRoom(ISFSObject data) throws Exception
	{
		try
		{
			int roomKey = data.getInt(ConstantClass.ROOM_KEY);
			Room delRoom = this.getParentZone().getRoomByName(String.valueOf(roomKey));
			if (delRoom != null)
			{
				this.getApi().removeRoom(delRoom);
			}
			// Tell Master Server Room is Deleted
			data.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_DELETE_ROOM);
			data.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
			WriteToMasterServer(data.toJson());
			
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("DeleteRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
		
	
	/*
	 * Common Functions
	 */
	
	private void LogMessage(ExtensionLogLevel level, String message)
	{
		if (!_showDetailLog && (level != ExtensionLogLevel.ERROR))
		{
			return;
		}
		trace(level, String.format("InGameExtension : %s", message));
	}
	
	private void LogMessage(String message)
	{
		LogMessage(ExtensionLogLevel.INFO, message);
	}

	private void SendErrorMessage(String command, String message, User user)
	{
		ISFSObject sendObj = new SFSObject();
		sendObj.putUtfString(ConstantClass.ERROR_CODE, message);
		sendObj.putUtfStringArray(ConstantClass.ERROR_MESSAGE, ErrorClass.GetErrorMessage(message));
		this.send(command, sendObj, user);
	}
	
	private void LoadDecoData()
	{
		try
		{
			DBManager dm = DBManager.getInstance();
			List<Object[]> result =  dm.LoadDecoData();
			_decoData.clear();
			for (Object[] obj : result)
			{
				int decoID = Integer.parseInt(obj[0].toString());
            	int price = Integer.parseInt(obj[1].toString());
            	_decoData.put(decoID, price);
			}
			trace(ExtensionLogLevel.INFO, "CapsaSusun GameExtension Deco Data Loaded.");
		}
		catch (Exception e) 
		{
			trace(ExtensionLogLevel.ERROR, String.format("LoadDecoData %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private int GetDecoPrice(int decoID)
	{
		if (_decoData.containsKey(decoID))
		{
			return _decoData.get(decoID);
		}
		return 0;
	}
	
	private void AlertMasterRoomIsGone(String roomList) throws Exception
	{
		ISFSObject sendObject = new SFSObject();
		sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CHECK_ROOM_EXIST);
		sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
		sendObject.putUtfString(ConstantClass.ROOM_LIST, roomList);
		WriteToMasterServer(sendObject.toJson());
	}
	
	private void CheckRoomExist(String roomList) throws Exception
	{
		ArrayList<String> splitRoom = new ArrayList<String>(Arrays.asList(roomList.split(":")));
		String notAvailablList = "";
		for(String roomName : splitRoom)
		{
			Room tempRoom = this.getParentZone().getRoomByName(roomName);
			if (tempRoom == null)
			{
				if (notAvailablList.isEmpty())
				{
					notAvailablList = roomName;
				}
				else
				{
					notAvailablList = String.format("%s:%s", notAvailablList, roomName);
				}
			}
		}
		if (!notAvailablList.isEmpty() )
		{
			AlertMasterRoomIsGone(notAvailablList);
		}
	}
	
	private void MasterAskRoomResync(int roomKey)
	{
		try
		{
			Room cekRoom = this.getParentZone().getRoomByName(Integer.toString(roomKey));
			if (cekRoom != null) 
			{
				ISFSObject sendObject = (SFSObject)cekRoom.getExtension().handleInternalMessage("GetSeatsInfo", null);
				sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
				sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_RESYNC_ROOM);
				WriteToMasterServer(sendObject.toJson());
			}
			else
			{
				LogMessage(ExtensionLogLevel.WARN, String.format("MasterAskRoomResync : Room %d Not found.", roomKey));
			}
		}
		catch(Exception e)
		{
			trace(ExtensionLogLevel.ERROR, String.format("MasterAskRoomResync Error : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	
	/*
	 * Hot Time Event
	 */
	
	private void UpdateHotTimeEvent(ISFSObject data) throws Exception
	{
		boolean isHotTime = data.getBool(ConstantClass.IS_HOT_TIME_EVENT);
		if (isHotTime)
		{
			_isHotTimeEvent = true;
			_hotTimeData = data;
			trace(ExtensionLogLevel.INFO, String.format("Hot Time Event Updated, Event Started : %s, Data : %s." , isHotTime, data.toJson()));
		}
		else
		{
			if(_isHotTimeEvent)
			{
				_isHotTimeEvent = false;
				_hotTimeData = null;
				trace(ExtensionLogLevel.INFO, "Hot Time Event Stoped");
			}
		}
	}
	
	/*
	 * Scheduler Class
	 */
	
	
	private class GameServerCycle implements Runnable
	{
		public void run()
		{
			try
			{
				// Check and Remove Ghost Room
				counterCleanRoom--;
				if (counterCleanRoom <= 0)
				{
					counterCleanRoom = cowndownCleanRoom;
					if (_roomRemoveList.size() > 0)
					{
						for(String roomName : _roomRemoveList)
						{
							Room checkRoom = InGameExtension.this.getParentZone().getRoomByName(roomName);
							if (checkRoom != null)
							{
								if (checkRoom.isEmpty())
								{
									InGameExtension.this.getApi().removeRoom(checkRoom);
									// TODO : alert Master
								}
							}
						}
						_roomRemoveList.clear();
					}
					List<Room> roomList = InGameExtension.this.getParentZone().getRoomList();
					if (roomList.size() > 0)
					{
						for(Room room : roomList)
						{
							if (room.isEmpty())
							{
								_roomRemoveList.add(room.getName());
							}
						}
					}
				}
				if (_isMasterConnected)
				{
					// Alert Master for Total Room & User Information
					counterInfoToMaster--;
					if (counterInfoToMaster <= 0)
					{
						counterInfoToMaster = coundownInfoToMaster;
						int totalUser = InGameExtension.this.getParentZone().getUserCount();
						int totalRoom = InGameExtension.this.getParentZone().getTotalRoomCount();
						if ((_lastTotalUser != totalUser) || (_lastTotalRoom != totalRoom))
						{
							ISFSObject sendObject = new SFSObject();
				        	sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_INFORMATION);
				        	sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
				        	sendObject.putInt(ConstantClass.TOTAL_ALL_USERS, totalUser);
				        	sendObject.putInt(ConstantClass.TOTAL_ALL_ROOMS, totalRoom);
				        	WriteToMasterServer(sendObject.toJson());
				        	_lastTotalUser = totalUser;
				        	_lastTotalRoom = totalRoom;
						}
					}
				}
				else
				{
					counterMasterConnect--;
					if (counterMasterConnect <= 0)
					{
						counterMasterConnect = 3;
						_clientBootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
			        	_clientBootstrap.setPipelineFactory(new NettyClientPipelineFactory());
			        	_clientBootstrap.setOption("tcpNoDelay", true);
			        	_clientBootstrap.setOption("keepAlive", true);
					   	_clientChannelFuture = _clientBootstrap.connect(new InetSocketAddress(_masterServerHost, _nettyPort));   	
						_clientChannel = _clientChannelFuture.sync().getChannel();
						
						_isMasterConnected = true;
						ISFSObject sendObject = new SFSObject();
			        	sendObject.putUtfString(ConstantClass.ACTION, ConstantClass.GAME_SERVER_CONNECTED);
			        	sendObject.putUtfString(ConstantClass.GAME_SERVER_HOST, _myHostname);
			        	sendObject.putInt(ConstantClass.CCU_LIMIT, _maxUserLimit);
			        	WriteToMasterServer(sendObject.toJson());
						
			        	if (_isMasterReconnect)
			        	{
			        		_isMasterReconnect = false;
			        		trace(ExtensionLogLevel.INFO, "CapsaSusun InGameExtension Netty Client Reconnected.");
			        	}
			        	else
			        	{
			        		trace(ExtensionLogLevel.INFO, "CapsaSusun InGameExtension Netty Client Started.");
			        	}
					}
				}
			}
			catch(Exception e)
			{
				trace(ExtensionLogLevel.ERROR, String.format("GameServerCycle : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
}
