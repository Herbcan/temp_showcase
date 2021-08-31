package mbs.diamond.GameExtension;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.ExtensionLogLevel;
import com.smartfoxserver.v2.extensions.SFSExtension;

import mbs.diamond.GameExtension.Game.Card;
import mbs.diamond.GameExtension.Game.CardComparator;
import mbs.diamond.GameExtension.Game.DragonWin;
import mbs.diamond.GameExtension.Game.Hand;
import mbs.diamond.GameExtension.Game.InstantWin;
import mbs.diamond.GameExtension.Game.Result;
import mbs.diamond.GameExtension.Game.RoundResult;
import mbs.diamond.capsasusun.ConstantClass;
import mbs.diamond.capsasusun.DBManager;
import mbs.diamond.capsasusun.DBManager.retUpdateCoin;
import mbs.diamond.capsasusun.ErrorClass;
import mbs.diamond.capsasusun.LeagueManager;

public class InRoomExtension extends SFSExtension
{
	// Game Info variables
	private enum RoomState
	{
		IDLE,
		WAIT_START_GAME,
		COUNTDOWN_START_GAME,
		SHUFFLE_CARD,
		DRAGON_CHECK,
		GAME_PLAY,
		COUNTDOWN_PLAY_GAME,
		RESULT
	}
	
	// TODO : Remove disconected
	private enum UserState
	{
		EMPTY,
		PLAYING,
		DONE,
		DISCONNECTED,
		DISCONNECTED_DONE,
		WAIT_NEXT_GAME
	}
	
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();
	
	private final float TAX_REGULAR = 0.055f;
	private final int FAST_PLAY_TIME = 30;
	private final int WIN_EXP = 59;
	private final int LOSE_EXP = 19;
	private final float[] expMultiplier = { 1f, 1.12f, 1.25f, 1.40f,
											1.57f, 1.76f, 1.97f, 2.21f, 2.48f,
											2.77f, 3.11f, 3.48f, 3.90f, 4.36f, 5.36f};
	// Beginner : 500, 1K, 3K ,5K -> 0-3
	// Expert   : 10K, 20K, 50K, 100k, 250K -> 4-8
	// Master   : 1M, 5M, 10M, 25M -> 9-12
	private final long[] _baseBetList = { 500, 1000, 3000, 5000,
										  10000, 20000, 50000, 100000, 250000,
										  1000000, 5000000, 10000000, 25000000};
	private final long[] minimumHandCoin = {5000, 30000, 90000, 125000, 250000, 500000, 1250000, 2500000, 5000000, 20000000, 100000000, 20000000, 500000000};
	private ArrayList<Long> _expTable = new ArrayList<Long>();
	private String _roomName;
	private String _createDate;
	private String _roomID;
	private long _baseBet;
	private boolean _isVip;
	private boolean _hasMission;
	private boolean _isRoomDestroyed;
	private boolean _isFastRoom;
	private boolean _creatorEnter;
	private int _betGrade;
	private int _creator;
	private int _match = 0;
	private int _currentMission;
	private float _currentTax;
	private Random _random = new Random();
	
	private RoomState _currentState = RoomState.WAIT_START_GAME;
	private int _readycount = 0;
	private int _totalPlayer;
	
	private boolean _isLeagueActive = true;
	private boolean _hasInstantWin = false;
	private boolean _hasDragon = false;
	private boolean _waitResult = false;
	private boolean _isPlaying = false;
	private int _dragonWinner = -1;
	private boolean _isGrandDragon = false;
	private int _dragonRank = 0;
	private long _StartResultTime = 0;
	
	// Player variables
	private ConcurrentHashMap<Integer, Integer> _userSeat = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, UserState> _userStatus = new ConcurrentHashMap<Integer, UserState>();
	private ConcurrentHashMap<Integer, Long> _userCoin = new ConcurrentHashMap<Integer, Long>();
	private ConcurrentHashMap<Integer, Long> _userExp = new ConcurrentHashMap<Integer, Long>();
	private ConcurrentHashMap<Integer, String> _userNickname = new ConcurrentHashMap<Integer, String>();
	private ConcurrentHashMap<Integer, String> _userType = new ConcurrentHashMap<Integer, String>();
	private ConcurrentHashMap<Integer, Integer> _userGender = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, String> _userIP = new ConcurrentHashMap<Integer, String>();
	
	private ConcurrentHashMap<Integer, Integer> _userOldWin = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Integer> _userOldPlay = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Integer> _userOldLevel = new ConcurrentHashMap<Integer, Integer>();
	
	private ConcurrentHashMap<Integer, Float> _userExpBuff = new ConcurrentHashMap<Integer, Float>();
	private ConcurrentHashMap<Integer, Float> _userTaxBuff = new ConcurrentHashMap<Integer, Float>();
	
	private ConcurrentHashMap<Integer, Integer> _userDeco = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Boolean> _userMission = new ConcurrentHashMap<Integer, Boolean>();
	
	private ConcurrentHashMap<Integer, Boolean> _userWaitResult = new ConcurrentHashMap<Integer, Boolean>();
	
	
	private ConcurrentHashMap<Integer, ArrayList<String>> _userCard = new ConcurrentHashMap<Integer, ArrayList<String>>();
	private ConcurrentHashMap<Integer, ArrayList<Integer>> _userMakeCard = new ConcurrentHashMap<Integer, ArrayList<Integer>>();
	
	private ConcurrentHashMap<Integer, Long> _userFloodTime = new ConcurrentHashMap<Integer, Long>();
	private ConcurrentHashMap<Integer, Integer> _userFloodNumber = new ConcurrentHashMap<Integer, Integer>();
	
	private ConcurrentHashMap<Integer, Integer> _userVipGrade = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Integer> _userLeagueGrade = new ConcurrentHashMap<Integer, Integer>();
	private ConcurrentHashMap<Integer, Integer> _userLeagueDivision = new ConcurrentHashMap<Integer, Integer>();
	
	private ArrayList<String> lastTotalResult = new ArrayList<String>();
	private int lastResultID = 0;
	private ArrayList<String> lastWinCard = new ArrayList<String>();
	private int lastDragonModifier = 0;
	
	// Scheduler
	private int _loopIndex = 0;
	private int _loopCounter;
	private int _loopCountDown = 0;
	private int _loopStepInterval = 100;
	ScheduledFuture<?> _loopHandle;
	
	
	// Event Hot Time
	private boolean _isHotTime = false;
	private int _hotTimeTarget = 0;
	private int _hotTimeMissionMulti = 1;
	private ConcurrentHashMap<Integer, Integer> _userHotTimePlay = new ConcurrentHashMap<Integer, Integer>();
	
	// Jackpot
	private boolean isWinJackpot = false;
	private String jackpotWinnerNickname = "";
	private String jackpotType = "";
	private int jackpotWinner = 0;
	private long jackpotWinAmount = 0;
	private long jackpotMinusAmount = 0;
	
	private boolean _showDetailLog = false;
	
	/*
	 * Extension
	 */
	
	@Override
	public void init()
	{	
		_roomName = this.getParentRoom().getName();
		_roomID = _roomName;
		_showDetailLog = (boolean)this.getParentZone().getExtension().handleInternalMessage("GetDetailLog", null);
		_isLeagueActive = (boolean)this.getParentZone().getExtension().handleInternalMessage("IsLeagueActive", null);
		LogMessage(ExtensionLogLevel.INFO, String.format("InRoomExtension %s created.", _roomName));
		
		Calendar now = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		_createDate = sdf.format(now.getTime());

		// Event Listener
		addEventHandler(SFSEventType.USER_JOIN_ROOM, RoomUserEnterHandler.class);
		addEventHandler(SFSEventType.USER_LEAVE_ROOM, RoomUserLeaveHandler.class);
		addEventHandler(SFSEventType.USER_DISCONNECT, RoomUserLeaveHandler.class);
		
		addRequestHandler(ConstantClass.BUY_DECO, RoomBuyDecoHandler.class);
		addRequestHandler(ConstantClass.SEND_ROOM_MESSAGE, RoomSendMessageHandler.class);
		addRequestHandler(ConstantClass.SEND_ROOM_EXPRESION, RoomSendExpresionHandler.class);
		addRequestHandler(ConstantClass.GAME_READY, RoomUserReadyResultHandler.class);
		addRequestHandler(ConstantClass.GAME_FINISH, RoomUserTimeoutDeckHandler.class);
		addRequestHandler(ConstantClass.RESORT_DECK, RoomUserResortHandler.class);
		addRequestHandler(ConstantClass.CURRENT_GAME_INFO, RoomGetGameInfoHandler.class);
		addRequestHandler(ConstantClass.CURRENT_ROOM_INFO, RoomGetRoomInfoHandler.class);
		addRequestHandler(ConstantClass.REQUEST_LEAVE_ROOM, RoomAskLeaveHandler.class);
		
		MakeExpTable();
		_isRoomDestroyed = false;
		_baseBet = 500;
		_betGrade = 0;
		_isVip = false;
		_hasMission = false;
		_isFastRoom = false;
		_creatorEnter = false;
		_currentTax = TAX_REGULAR;
		_currentMission = ConstantClass.MISSION_NONE;

		for(int i = 0; i < 4; i++)
		{
			_userSeat.put(i, -1);
			_userCoin.put(i, 0L);
			_userStatus.put(i, UserState.EMPTY);
			_userNickname.put(i, "-");
			_userType.put(i, "");
			_userGender.put(i, 0);
			_userExp.put(i, 0L);
			_userTaxBuff.put(i, 1f);
			_userExpBuff.put(i, 0f);
			_userDeco.put(i, 0);
			_userIP.put(i, "");
			_userVipGrade.put(i, 0);
			_userLeagueGrade.put(i, 0);
			_userLeagueDivision.put(i, 1);

			_userFloodTime.put(i, 0L);
			_userFloodNumber.put(i, 0);

			_userHotTimePlay.put(i, 0);
			
			_userWaitResult.put(i, false);
			
			_userOldWin.put(i, 0);
			_userOldPlay.put(i, 0);
			_userOldLevel.put(i, 0);
			
			lastTotalResult.add(i,"-1:-1:1:-1:0:0:0:0");
		}
		ClearForNextMatch();
		
		
		_loopIndex = 0;
		_loopCounter = 60000;
		SmartFoxServer sfs = SmartFoxServer.getInstance();
		
		boolean cekHotTime = (boolean)this.getParentZone().getExtension().handleInternalMessage("IsHotTimeEvent", null);
		if (cekHotTime)
		{
			_isHotTime = true;
		}
        _loopHandle = sfs.getTaskScheduler().scheduleAtFixedRate(new GameLoop(), 100, _loopStepInterval, TimeUnit.MILLISECONDS);
	}

	@Override
	public void destroy() 
	{
		_isRoomDestroyed = true;
		trace(ExtensionLogLevel.INFO, String.format("InRoomExtension %s destroyed.", _roomName));
		_loopHandle.cancel(true);
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
				case "RoomSetup":
					RoomSetup((SFSObject)params);
					break;
				case "GetSeatsInfo":
					returnObj = GetSeatsInfo();
					break;
				case "UserEnterRoom":
					UserEnterRoom((SFSObject)params);
					break;
				case "UserLeaveRoom":
					UserLeaveRoom((SFSObject)params);
					break;
				case "UserAskLeaveRoom":
					UserAskLeaveRoom((SFSObject)params);
					break;
				case "UserBuyDeco":
					UserBuyDeco((SFSObject)params);
					break;
				case "UserSendMessage":
					UserSendMessage((SFSObject)params);
					break;
				case "UserSendExpresion":
					UserSendExpresion((SFSObject)params);
					break;
				case "GetCurrentGameInfo":
					GetCurrentGameInfo((SFSObject)params);
					break;
				case "GetCurrentRoomInfo":
					GetCurrentRoomInfo((SFSObject)params);
					break;
				case "UserResultReady":
					UserResultReady((SFSObject)params);
					break;
				case "UserTimeoutDeck":
					UserTimeoutDeck((SFSObject)params);
					break;
				case "UserResortDeck":
					UserResortDeck((SFSObject)params);
					break;
				default:
					LogMessage(ExtensionLogLevel.ERROR, String.format("handleInternalMessage : Unknown Command : %s.", cmdName));	
					break;
			}
		}
		catch(Exception e)
		{
			returnObj = null;
			LogMessage(ExtensionLogLevel.ERROR, String.format("handleInternalMessage : %s.", ErrorClass.StackTraceToString(e)));	
		}
		return returnObj;
	}
	
	/*
	 * Common Functions
	 */

	private void MakeExpTable()
	{
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
	
	public void SendErrorMessage(String command, String message, User user)
	{
		ISFSObject sendObj = new SFSObject();
		sendObj.putUtfString(ConstantClass.ERROR_CODE, message);
		sendObj.putUtfStringArray(ConstantClass.ERROR_MESSAGE, ErrorClass.GetErrorMessage(message));
		sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
		this.send(command, sendObj, user);
	}

	private void SendInterlaMessage(String cmdName, ISFSObject params)
	{
		params.putInt(ConstantClass.ROOM_KEY, Integer.parseInt(_roomName));
		params.putInt(ConstantClass.ROOM_CREATOR, _creator);
		params.putInt(ConstantClass.BASE_BET, _betGrade);
		params.putBool(ConstantClass.IS_VIP, _isVip);
		params.putBool(ConstantClass.IS_TOURNAMENT, false);
		this.getParentZone().getExtension().handleInternalMessage(cmdName, params);
	}
	
	public void LogMessage(ExtensionLogLevel level, String message)
	{
		if (!_showDetailLog && (level != ExtensionLogLevel.ERROR))
		{
			return;
		}
		trace(level, String.format("InRoomExtension Room : %s -> %s", _roomName, message));
	}
	
	public void LogMessage(String message)
	{
		LogMessage(ExtensionLogLevel.INFO, message);
	}

	private void ClearForNextMatch()
	{
		isWinJackpot = false;
		jackpotWinnerNickname = "";
		jackpotType = "";
		jackpotWinner = 0;
		jackpotWinAmount = 0;
		jackpotMinusAmount = 0;
		lastWinCard.clear();
		lastDragonModifier = 0;	
		_currentMission = ConstantClass.MISSION_NONE;
		_userCard.clear();
		for(int i = 0; i < 4; i++)
		{
			_userMission.put(i, false);
			_userWaitResult.put(i, false);
		}
	}
	
	private int GetPlayerCount()
	{
		int nplayers = 0;
		for(int i = 0; i < _userSeat.size(); i++)
		{
			if(_userSeat.get(i) != -1)
			{
				nplayers++;
			}
		}
		return nplayers;
	}

	private int GetTotalPlayer()
	{
		int nPlayers = 0;
		for(int i = 0; i < 4; i++)
		{
			if (_userSeat.get(i) >= 0)
			{
				if((_userStatus.get(i) == UserState.PLAYING ) || (_userStatus.get(i) == UserState.DONE) || (_userStatus.get(i) == UserState.DISCONNECTED) || (_userStatus.get(i) == UserState.DISCONNECTED_DONE))
				{
					nPlayers++;	
				}
			}
		}
		return nPlayers;
	}

	public long GetBaseBet()
	{
		return _baseBet;
	}
	
	private ArrayList<User> GetUserList(int except)
	{
		ArrayList<User> userList = new ArrayList<User>();
		for(int i = 0; i < 4; i++)
		{
			int id =_userSeat.get(i);
			if ((id >= 0) && (id != except))
			{
				User user = this.getParentRoom().getUserByName(Integer.toString(id));
				if (user != null)
				{
					userList.add(user);
				}
			}
		}
		return userList;
	}
	
	private ArrayList<User> GetUserListExceptWait()
	{
		ArrayList<User> userList = new ArrayList<User>();
		for(int i = 0; i < 4; i++)
		{
			int id =_userSeat.get(i);

			if ((id >= 0) &&  _userStatus.get(i) != UserState.WAIT_NEXT_GAME)
			{
				User user = this.getParentRoom().getUserByName(Integer.toString(id));
				if (user != null)
				{
					userList.add(user);
				}
			}
		}
		return userList;
	}
	
	public int getUserSeat(int playerID)
	{
		int temp = -1;
		for ( int i = 0; i < 4; i++)
		{
			if (_userSeat.get(i) == playerID)
			{
				temp = i;
				break;
			}
		}
		return temp;
	}
	
	public boolean get_WaitingUser(int playerid)
	{
		int seatnum = getUserSeat(playerid);
		if(_userStatus.get(seatnum) == UserState.WAIT_NEXT_GAME)
		{
			return true;
		}
		return false;
	}
	
	public int getUserPidx(int seatnum)
	{
		return _userSeat.get(seatnum);
	}
	
	public long getCurrentExp(int seat)
	{
		long currentExp = _userExp.get(seat);
		return currentExp;
	}
	
	public long AddExp(int win,int seat)
	{
		long currentExp = _userExp.get(seat);
		if ( win == 1)
		{
			float gainExp = WIN_EXP * expMultiplier[_betGrade];
			currentExp += Math.round(gainExp + (gainExp * _userExpBuff.get(seat)));
		}
		else
		{
			float gainExp = LOSE_EXP * expMultiplier[_betGrade];
			currentExp += Math.round(gainExp + (gainExp * _userExpBuff.get(seat)));
		}
		if( currentExp > _expTable.get(_expTable.size() - 1))
		{
			currentExp = _expTable.get(_expTable.size() - 1);
		}
		_userExp.put(seat, currentExp);
		return currentExp;
	}
	
	public int get_currentMission()
	{
		return _currentMission;
	}
	
	public ConcurrentHashMap<Integer, Long> GetUserCoinClone()
	{
		ConcurrentHashMap<Integer, Long> clone = new ConcurrentHashMap<Integer, Long>(_userCoin);
		return clone;
	}
	
	public long GetUserCoin(int seat)
	{
		return _userCoin.get(seat);
	}
	
	public void putUserCoin(int seat, long coin)
	{
		_userCoin.put(seat, coin);
	}

	public float get_currentTax()
	{
		return _currentTax;
	}
	
	public float get_currentTax(int seat)
	{
		float taxbuff = _userTaxBuff.get(seat);
		return _currentTax * taxbuff;
	}

	public boolean IsFastRoom()
	{
		return _isFastRoom;
	}
	
	public boolean IsMissionRoom()
	{
		return _hasMission;
	}

	public int getCurrentState() 
	{
		int ret = 0;
		switch(_currentState)
		{
			case IDLE:
				ret = 1;
				break;
			case WAIT_START_GAME:
				ret = 2;
				break;
			case COUNTDOWN_START_GAME:  
				ret = 3;
				break;
			case SHUFFLE_CARD:
				ret = 4;
				break;
			case DRAGON_CHECK:
				ret = 5;
				break;
			case GAME_PLAY:
				ret = 6;
				break;
			case COUNTDOWN_PLAY_GAME:
				ret = 7;
				break;
			case RESULT:
				ret = 8;
				break;
			default:
				ret = 0;
				break;
		}
		return ret;
	}
	
	public String GetUserType(int seat)
	{
		return _userType.get(seat);
	}

	public boolean IsLeagueActive()
	{
		return _isLeagueActive;
	}
	
	public boolean isHotTime()
	{
		return _isHotTime;
	}
	  
	public int GetHotTimeMulti()
	{
		return _hotTimeMissionMulti;
	}
	
	/*
	 * Room Event
	 */
	
	private void RoomSetup(ISFSObject data) throws Exception
	{
		try
		{
			LogMessage(String.format("Handle RoomSetup : %s", data.toJson()));	
			_isVip = data.getBool(ConstantClass.IS_VIP);
			_hasMission = data.getBool(ConstantClass.HAS_MISSION);
			_creator = data.getInt(ConstantClass.ROOM_CREATOR);
			_betGrade = data.getInt(ConstantClass.BASE_BET);
			_isFastRoom = data.getBool(ConstantClass.FAST_ROOM);
			
			_baseBet = _baseBetList[_betGrade];
			
			// Get Creator Data from Database
			String nickname = "";
			long inHandMoney = 0;
			int gender = 0;
			long exp = 0;
			int vipGrade = 0;
			String loginType = "Guest";
			int vipExpBonus = 0;
			int leagueGrade = 0;
			int leagueDivision = 1;
				
			DBManager dm = DBManager.getInstance();
			Object[] ret = dm.GetUserData(_creator);

			loginType = ret[0].toString();
			gender = (boolean) (ret[1]) ? 1 : 0;
			BigInteger largeValue = new BigInteger(ret[2].toString());
	        inHandMoney = largeValue.longValue();
	        nickname = ret[3].toString();
	        exp = Long.parseLong(ret[4].toString());
	        vipGrade = Integer.parseInt(ret[7].toString());
	        vipExpBonus = Integer.parseInt(ret[11].toString());
	        leagueGrade =  Integer.parseInt(ret[12].toString());
        	leagueDivision = Integer.parseInt(ret[14].toString());

			_userSeat.put(0, _creator);
			_userStatus.put(0, UserState.DISCONNECTED);
			_userNickname.put(0, nickname);
			_userCoin.put(0, inHandMoney);
			_userGender.put(0, gender);
			_userExp.put(0, exp);
			_userType.put(0, loginType);
			_userDeco.put(0, 0);
			_userVipGrade.put(0, vipGrade);
			_userLeagueGrade.put(0, leagueGrade);
			_userLeagueDivision.put(0, leagueDivision);
			_userExpBuff.put(0, (vipExpBonus / 100.0f));
			_userOldLevel.put(0, GetLevel(exp));

			ISFSObject sendObj = GetSeatsInfo();
			SendInterlaMessage(ConstantClass.GAME_SERVER_CREATE_ROOM_SUCCESS, sendObj);

			_loopIndex = 5;
			_loopCounter = 30000;
			LogMessage("Room Setup finished.");
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("RoomSetup : %s.", ErrorClass.StackTraceToString(e)));	
		}
	}
	
	private void UserAskLeaveRoom(ISFSObject data) throws Exception
	{
		try
		{
			String userName = data.getUtfString(ConstantClass.USER_ID);
			User user = this.getParentRoom().getUserByName(userName);
			if (user == null)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("UserAskLeaveRoom : User %s not found.", userName));
				return;
			}
			int playerID = Integer.parseInt(userName);
			int seat = getUserSeat(playerID);
			if (seat < 0)
			{
				ISFSObject sendObj = new SFSObject();
				sendObj.putBool(ConstantClass.REQUEST_RESULT, true);
				this.send(ConstantClass.REQUEST_LEAVE_ROOM, sendObj, user);
				return;
			}
			boolean allowed = false;
			if(_isPlaying)
			{
				if( _userStatus.get(seat) == UserState.WAIT_NEXT_GAME)
				{
					allowed = true;
				}
			}
			else
			{
				allowed = true;
				if (_waitResult)
				{
					if (!_userWaitResult.get(seat))
					{
						allowed = false;
					}
				}
			}
			ISFSObject sendObj = new SFSObject();
			sendObj.putBool(ConstantClass.REQUEST_RESULT, allowed);
			this.send(ConstantClass.REQUEST_LEAVE_ROOM, sendObj, user);
			if (allowed)
			{
				HandleUserLeave(playerID, seat, "User Ask Leave");
			}
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserAskLeaveRoom : %s.", ErrorClass.StackTraceToString(e)));	
		}
	}

	private void UserEnterRoom(ISFSObject data) throws Exception
	{
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User newUser = this.getParentRoom().getUserByName(userName);
		if (newUser == null)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserEnterRoom : User %s not found.", userName));
			return;
		}
		if(_isRoomDestroyed)
		{
			SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.ROOM_NOT_AVAILABLE, newUser);
			this.getParentRoom().removeUser(newUser);
			return;
		}
		
		int playerID = Integer.parseInt(newUser.getName());
		boolean SendRoomInfo = false;
		boolean sendReconnect = false;
		boolean creatorEnter = false;
		int selectedSeat = -1;
		try
		{
			if (checkReconnect(playerID))
			{
				SendRoomInfo = true;
				
				if (playerID == _creator)
				{
					if(!_creatorEnter)
					{
						_creatorEnter = true;
						creatorEnter = true;
					}
				}
				if (!creatorEnter)
				{
					if (_isPlaying)
					//if ((_currentState != RoomState.IDLE) || (_currentState != RoomState.WAIT_START_GAME) || (_currentState != RoomState.COUNTDOWN_START_GAME))
					{
						sendReconnect = true;
					}
					LogMessage(String.format("PlayerID %d reconnect. current room status = %s", playerID,_currentState.toString()));
				}
				
				int backSeat = getUserSeat(playerID);
				_userIP.put(backSeat, newUser.getIpAddress());
			}
			else
			{
	        	String nickname = "";
				long inHandMoney = 0;
				int gender = 0;
				long exp = 0;
				int vipGrade = 0;
				String loginType = "Guest";
				int vipExpBonus = 0;
				int leagueGrade = 0;
				int leagueDivision = 1;
				
				DBManager dm = DBManager.getInstance();
				Object[] ret = dm.GetUserData(playerID);

				loginType = ret[0].toString();
				gender = (boolean) (ret[1]) ? 1 : 0;
				BigInteger largeValue = new BigInteger(ret[2].toString());
	        	inHandMoney = largeValue.longValue();
	        	nickname = ret[3].toString();
	        	exp = Long.parseLong(ret[4].toString());
	        	vipGrade = Integer.parseInt(ret[7].toString());
	        	vipExpBonus = Integer.parseInt(ret[11].toString());
	        	leagueGrade =  Integer.parseInt(ret[12].toString());
	        	leagueDivision = Integer.parseInt(ret[14].toString());
	        	
				if (inHandMoney < minimumHandCoin[_betGrade])
				{
					LogMessage(String.format("PlayerID %d InHandMoney is lower than BaseBet minimum value.", playerID));
					
					SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.ENTER_ROOM_NOT_ENOUGH_COIN, newUser);
					this.getParentRoom().removeUser(newUser);
					return;
				}
				
				/* TODO : for now, Vip allow same IP
				if (isVip)
				{
					String newIP = newUser.getIpAddress();
					
					for(int i = 0; i < 4; i++)
					{
						if (_userIP.get(i).equals(newIP))
						{
							LogMessage(String.format("PlayerID %d same IP with PlayerID %d. the IP : %s", playerID, _userSeat.get(i), newIP));
							
							SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.SAME_IP_PRIVATE_ROOM, newUser);
							this.getParentRoom().removeUser(newUser);
							return;
						}
					}
				}
				*/
				
				for(int i = 0; i < 4; i++)
				{
					if (_userSeat.get(i) == -1)
					{
						selectedSeat = i;
						break;
					}
				}
				
				if (selectedSeat < 0)
				{
					LogMessage(String.format("Room is Full, PlayerID %d Unable to Enter.", playerID));
					
					SendErrorMessage(ConstantClass.JOIN_ROOM, ErrorClass.ROOM_ALREADY_FULL, newUser);
					this.getParentRoom().removeUser(newUser);
					return;
				}
						
				SendRoomInfo = true;
				_userSeat.put(selectedSeat, playerID);
				_userNickname.put(selectedSeat, nickname);
				_userCoin.put(selectedSeat, inHandMoney);
				_userMission.put(selectedSeat, false);
				_userGender.put(selectedSeat, gender);
				_userExp.put(selectedSeat, exp);
				_userDeco.put(selectedSeat, 0);
				_userType.put(selectedSeat, loginType);	
				_userVipGrade.put(selectedSeat, vipGrade);
				_userLeagueGrade.put(selectedSeat, leagueGrade);
				_userLeagueDivision.put(selectedSeat, leagueDivision);
				_userExpBuff.put(selectedSeat, (vipExpBonus / 100.0f));
				_userOldLevel.put(selectedSeat, GetLevel(exp));
				
				_userIP.put(selectedSeat, newUser.getIpAddress());
				
				boolean nextGame = false;
				if (!_isPlaying)
				//if ( _currentState == RoomState.IDLE || _currentState == RoomState.WAIT_START_GAME ||  _currentState == RoomState.COUNTDOWN_START_GAME )
				{
					_userStatus.put(selectedSeat, UserState.PLAYING);
					if(_waitResult)
					{
						nextGame = true;
						_userWaitResult.put(selectedSeat, true);
					}
				}
				else 
				{
					nextGame = true;
					_userStatus.put(selectedSeat, UserState.WAIT_NEXT_GAME);
				}
				
				//Inform all users, new user sitting on what chair
				ISFSObject sendObj = new SFSObject();
				ISFSObject newUserData = new SFSObject();
				newUserData.putInt(ConstantClass.USER_ID, playerID);
				newUserData.putLong(ConstantClass.COINS, inHandMoney);
				newUserData.putUtfString(ConstantClass.NICKNAME, nickname);
				newUserData.putInt(ConstantClass.GENDER, gender);
				newUserData.putLong(ConstantClass.EXP, exp);
				newUserData.putInt(ConstantClass.LEVEL, GetLevel(exp)); 
				newUserData.putInt(ConstantClass.DECO_ID, 0);
				newUserData.putInt(ConstantClass.VIP_GRADE, vipGrade);
				newUserData.putInt(ConstantClass.LEAGUE_GRADE, leagueGrade);
				newUserData.putInt(ConstantClass.LEAGUE_DIVISION, leagueDivision);
				if (_isVip)
				{
					if (playerID == _creator)
					{
						newUserData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
					}
					else
					{
						newUserData.putBool(ConstantClass.CAN_INVITE_PLAYER, false); 
					}
				}
				else
				{
					newUserData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
				}
		        sendObj.putSFSObject(ConstantClass.USER_DATA, newUserData);
		        sendObj.putInt(ConstantClass.SEAT_INDEX, selectedSeat);
		        sendObj.putBool(ConstantClass.IS_WAIT_NEXT_GAME, nextGame);
		        sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
		        
		        this.send(ConstantClass.USER_ENTER_ROOM, sendObj, GetUserList(playerID));
		      
		        // Alert Master Server
		        ISFSObject parseObj = new SFSObject();
		        parseObj.putInt(ConstantClass.USER_ID, playerID);
		        parseObj.putInt(ConstantClass.GENDER, gender);
		        parseObj.putInt(ConstantClass.LEVEL, GetLevel(exp));
		        parseObj.putInt(ConstantClass.VIP_GRADE, vipGrade);
		        parseObj.putInt(ConstantClass.LEAGUE_GRADE, leagueGrade);
		        parseObj.putInt(ConstantClass.LEAGUE_DIVISION, leagueDivision);
		        parseObj.putUtfString(ConstantClass.NICKNAME, nickname);
		        parseObj.putInt(ConstantClass.SEAT_INDEX, selectedSeat + 1);
		        SendInterlaMessage(ConstantClass.USER_ENTER_ROOM, parseObj);
				LogMessage(String.format("PlayerID %d Enter Room on seat %d", playerID, selectedSeat));  
			}
		}
		catch (Exception e)
		{
			SendRoomInfo = false;
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserEnterRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
		try
		{
			if (SendRoomInfo)
			{
				// Inform new user all the current player status
				ISFSObject seatsInfo = new SFSObject();
				for ( int i = 0; i < 4; i++)
				{
					ISFSObject userData = new SFSObject();
					if ( _userSeat.get(i) >= 0)
					{
						userData.putInt(ConstantClass.USER_ID, _userSeat.get(i));
						userData.putInt(ConstantClass.GENDER, _userGender.get(i));
						userData.putLong(ConstantClass.COINS, _userCoin.get(i));
						userData.putLong(ConstantClass.EXP, _userExp.get(i));
						userData.putInt(ConstantClass.LEVEL, GetLevel(_userExp.get(i)));
						userData.putInt(ConstantClass.DECO_ID, _userDeco.get(i));
						userData.putInt(ConstantClass.VIP_GRADE, _userVipGrade.get(i));
						userData.putInt(ConstantClass.LEAGUE_GRADE, _userLeagueGrade.get(i));
						userData.putInt(ConstantClass.LEAGUE_DIVISION, _userLeagueDivision.get(i));
						userData.putUtfString(ConstantClass.NICKNAME, _userNickname.get(i));
						if ( _userStatus.get(i) == UserState.WAIT_NEXT_GAME)
						{
							userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, true);
						}
						else
						{
							userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, false);
						}
						if (_isVip)
						{
							if (_userSeat.get(i) == _creator)
							{
								userData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
							}
							else
							{
								userData.putBool(ConstantClass.CAN_INVITE_PLAYER, false); 
							}
						}
						else
						{
							userData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
						}
					}
					else
					{
						userData.putInt(ConstantClass.USER_ID, -1);
						userData.putInt(ConstantClass.GENDER, 0);
						userData.putLong(ConstantClass.COINS, 0);
						userData.putLong(ConstantClass.EXP, 0);
						userData.putInt(ConstantClass.LEVEL, 1);
						userData.putInt(ConstantClass.DECO_ID, 0);
						userData.putInt(ConstantClass.VIP_GRADE, 0);
						userData.putInt(ConstantClass.LEAGUE_GRADE, 0);
						userData.putInt(ConstantClass.LEAGUE_DIVISION, 1);
						userData.putUtfString(ConstantClass.NICKNAME, "-");
						userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, false);
						userData.putBool(ConstantClass.CAN_INVITE_PLAYER, false);
					}
					seatsInfo.putSFSObject(Integer.toString(i), userData);
				}
				seatsInfo.putLong(ConstantClass.BASE_BET, _baseBet);
				seatsInfo.putUtfString(ConstantClass.ROOM_KEY, _roomName);
				seatsInfo.putBool(ConstantClass.HAS_MISSION, _hasMission);
				seatsInfo.putInt(ConstantClass.MISSION_ID, _currentMission);
				seatsInfo.putBool(ConstantClass.IS_VIP, _isVip);
				seatsInfo.putBool(ConstantClass.FAST_ROOM, _isFastRoom);
				seatsInfo.putBool(ConstantClass.IS_RECONNECT, sendReconnect);
				seatsInfo.putInt(ConstantClass.PLAYING_STATUS, getCurrentState());
				seatsInfo.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
				seatsInfo.putBool(ConstantClass.IS_TOURNAMENT, false);
				seatsInfo.putBool(ConstantClass.IS_HOT_TIME_EVENT, _isHotTime);
				seatsInfo.putInt(ConstantClass.HOT_TIME_EVENT_TARGET, _hotTimeTarget);
				seatsInfo.putInt(ConstantClass.HOT_TIME_EVENT_CURRENT, _userHotTimePlay.get(getUserSeat(playerID)));
				seatsInfo.putInt(ConstantClass.HOT_TIME_MISSION_MULTI, _hotTimeMissionMulti);
				this.send(ConstantClass.ROOM_INFORMATION, seatsInfo, newUser);
			}
			else
			{
				this.getParentRoom().removeUser(newUser);
			}
			if (creatorEnter)
			{
				if ( GetPlayerCount() == 1)
				{
					this.send(ConstantClass.WAIT_FOR_OTHER_USER, new SFSObject(), newUser);
				}
				LogMessage(String.format("PlayerID %d Enter Room as creator.", playerID));
			}
			if(_loopIndex == 5)
			{
				_loopIndex = 100;
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserEnterRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void UserLeaveRoom(ISFSObject data) throws Exception
	{
		int playerID = data.getInt(ConstantClass.USER_ID);
		String reason = data.getUtfString(ConstantClass.REASON);
		try
		{
			int seat = getUserSeat(playerID);
			if (seat < 0)
			{
				return;
			}
			if(_isPlaying)
			{
				UserState state = _userStatus.get(seat);
				if (state == UserState.PLAYING)
				{
					_userStatus.put(seat, UserState.DISCONNECTED);
					LogMessage(String.format("PlayerID %d disconnected, reason %s, wait for rejoin. current room status = %s", playerID,reason,_currentState.toString()));
				}
				else if(state == UserState.DONE)
				{
					_userStatus.put(seat, UserState.DISCONNECTED_DONE);	
					LogMessage(String.format("PlayerID %d disconnected, reason %s, wait for rejoin. current room status = %s", playerID,reason,_currentState.toString()));
				}
				else if(state == UserState.WAIT_NEXT_GAME)
				{
					HandleUserLeave(playerID, seat, reason);
				}
				else
				{
					LogMessage(ExtensionLogLevel.ERROR, String.format("PlayerID %d with State %s leave room, reason %s, current room status = %s", playerID,state.toString(), reason,_currentState.toString()));
				}
			}
			else 
			{
				if (_waitResult)
				{
					if (_userWaitResult.get(seat))
					{
						HandleUserLeave(playerID, seat, reason);
					}
					else
					{
						_userStatus.put(seat, UserState.DISCONNECTED);
					}
				}
				else
				{
					HandleUserLeave(playerID, seat, reason);
				}
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserLeaveRoom : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void HandleUserLeave(int playerID, int seat, String reason) throws Exception
	{	
		if(_creator == playerID)
		{
			_creator = -1;
		}
		
		_userSeat.put(seat, -1);
		_userCoin.put(seat, 0L);
		_userMission.put(seat, false);
		_userNickname.put(seat, "-");
		_userStatus.put(seat, UserState.EMPTY);
		_userGender.put(seat, 0);
		_userExp.put(seat, 0L);
		_userTaxBuff.put(seat, 1f);
		_userExpBuff.put(seat, 0f);
		_userDeco.put(seat, 0);
		_userIP.put(seat, "");
		_userVipGrade.put(seat, 0);
		_userLeagueGrade.put(seat, 0);
		_userLeagueDivision.put(seat, 1);
				
		_userFloodTime.put(seat, 0L);
		_userFloodNumber.put(seat, 0);
		
		_userHotTimePlay.put(seat, 0);
		
		_userWaitResult.put(seat, false);
		_userOldWin.put(seat, 0);
		_userOldPlay.put(seat, 0);
		_userOldLevel.put(seat, 0);
		
		ISFSObject sendObj = new SFSObject();
        sendObj.putInt(ConstantClass.USER_ID, playerID);
        sendObj.putInt(ConstantClass.SEAT_INDEX, seat);
        sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
        this.send(ConstantClass.USER_LEAVE_ROOM, sendObj, GetUserList(-1));
        
        // Alert user leave to master
        ISFSObject parseObj = new SFSObject();
        parseObj.putInt(ConstantClass.USER_ID, playerID);
        parseObj.putInt(ConstantClass.SEAT_INDEX, seat + 1);
        SendInterlaMessage(ConstantClass.USER_LEAVE_ROOM, parseObj);
        LogMessage(String.format("PlayerID %d with seat %d leave Room, Reason %s.", playerID, seat, reason));
	}
	
	private void UserBuyDeco(ISFSObject data) throws Exception
	{
		int decoID = data.getInt(ConstantClass.DECO_ID);
		ArrayList<Integer> listUser = (ArrayList<Integer>)data.getIntArray(ConstantClass.USER_LIST);
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User user = this.getParentRoom().getUserByName(userName);
		if (user == null)
		{
			LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : User %s not found.", userName));
			return;
		}
		try
		{
			int basePrice = (int)this.getParentZone().getExtension().handleInternalMessage("GetDecoPrice", decoID);
			if (basePrice <= 0)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
    			LogMessage(ExtensionLogLevel.ERROR, String.format("UserBuyDeco : DecoID %d not found in database.", decoID));
    			return;
			}
			int playerID = Integer.parseInt(user.getName());
			if (listUser == null)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
				LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : PlayerID %s send empty list.", playerID));
				return;
			}
			int nList = listUser.size();
			if (nList <= 0)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
				LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : PlayerID %s not buy for anyone.", playerID));
				return;
			}
			boolean passed = true;
			for(int i = 0; i < nList; i++)
			{
				int gainID = listUser.get(i);
				int seat = getUserSeat(gainID);
				if ( seat == -1)
				{
					passed = false;
					break;
				}
			}
			if (!passed)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
				LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : PlayerID %s buy for someone not in seat.", playerID));
				return;
			}
			
			int userSeat = getUserSeat(playerID);
			if (userSeat < 0)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
				LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : PlayerID %s not in game.", playerID));
				return;
			}
		

			long newCoin = DBManager.getInstance().GetInHandMoney(playerID);
			_userCoin.put(userSeat, newCoin);
			    						
			int totalPrice = basePrice * nList;
			long reducedCoin = _userCoin.get(userSeat) - totalPrice;
			
			if (reducedCoin < 0)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.DATA_MISMATCH, user);
				LogMessage(ExtensionLogLevel.WARN, String.format("UserBuyDeco : PlayerID %s not enough coin.", playerID));
				return;
			}
			retUpdateCoin coinret = DBManager.getInstance().updatePlayerCoinAdd(playerID, totalPrice*-1, -1);
			
			if(coinret.ErrCode == 1)
			{
				SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.SERVER_UNKNOWN_ERROR, user);
				LogMessage(ExtensionLogLevel.ERROR, "UserBuyDeco SP updatePlayerCoin Error.");
				return;
			}
			
			_userCoin.put(userSeat, reducedCoin);
			for(int i = 0; i < nList; i++)
			{
				int gainID = listUser.get(i);
				int seat = getUserSeat(gainID);
				if ( seat >= 0)
				{
					_userDeco.put(seat, decoID);
				}
			}
			ISFSObject sendObj = new SFSObject();
			sendObj.putInt(ConstantClass.DECO_ID, decoID);
			sendObj.putInt(ConstantClass.SENDER_ID, playerID);
			sendObj.putInt(ConstantClass.SEAT_INDEX, userSeat);
			sendObj.putIntArray(ConstantClass.USER_LIST, listUser);
			sendObj.putLong(ConstantClass.COINS, reducedCoin);
			sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
			this.send(ConstantClass.GET_DECO, sendObj, GetUserList(-1));
				
			int logret = DBManager.getInstance().InsertLogCoinExpense(playerID, "02", BigInteger.valueOf(Long.valueOf(totalPrice)),BigInteger.valueOf(Long.valueOf(reducedCoin)),String.valueOf(decoID));
			if(logret != 0)
			{
				LogMessage(ExtensionLogLevel.ERROR, "UserBuyDeco SP InsertLogCoinExpense Error.");
			}
		}
		catch (Exception e) 
		{
			SendErrorMessage(ConstantClass.BUY_DECO, ErrorClass.SERVER_UNKNOWN_ERROR, user);
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserBuyDeco : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void UserSendExpresion(ISFSObject data) throws Exception
	{
		int id = data.getInt(ConstantClass.EXPRESION_ID);
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User user = this.getParentRoom().getUserByName(userName);
		if (user == null)
		{
			LogMessage(ExtensionLogLevel.WARN, String.format("UserSendExpresion : User %s not found.", userName));
			return;
		}
		try
		{
			int playerID = Integer.parseInt(user.getName());
			int seat = getUserSeat(playerID);
			if (seat < 0)
			{
				KickUser(user, "UserSendExpresion : Player not in game.", 0);
				return;
			}
			long lastTime = _userFloodTime.get(seat);
			int lastCounter = _userFloodNumber.get(seat);
			long currentTime = System.currentTimeMillis();
			boolean passed = true;
			if ( currentTime >= (lastTime+1000))
			{
				lastTime = currentTime;
				lastCounter = 1;
			}
			else
			{
				lastCounter++;
				if(lastCounter > 5)
				{
					passed = false;
				}
			}
			_userFloodTime.put(seat, lastTime);
			_userFloodNumber.put(seat, lastCounter);
			if (passed)
			{
				SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
				ISFSObject sendObj = new SFSObject();
				sendObj.putInt(ConstantClass.SENDER_ID, playerID);
				sendObj.putInt(ConstantClass.SEAT_INDEX, seat);
				sendObj.putInt(ConstantClass.EXPRESION_ID, id);
				sendObj.putUtfString(ConstantClass.NICKNAME, _userNickname.get(seat));
				sendObj.putUtfString(ConstantClass.TIME, dateFormat.format(new Date()));
				sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
				this.send(ConstantClass.GET_ROOM_EXPRESION, sendObj, GetUserList(-1));
			}
			else
			{
				KickUser(user, "UserSendExpresion : Flooding.", 0);
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserSendExpresion : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void UserSendMessage(ISFSObject data) throws Exception
	{
		String msg = data.getUtfString(ConstantClass.MESSAGE);
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User user = this.getParentRoom().getUserByName(userName);
		if (user == null)
		{
			LogMessage(ExtensionLogLevel.WARN, String.format("UserSendMessage : User %s not found.", userName));
			return;
		}
		try
		{
			int playerID = Integer.parseInt(user.getName());
			int seat = getUserSeat(playerID);
			if (seat < 0)
			{
				KickUser(user, "UserSendMessage : Player not in game.", 0);
				return;
			}
			long lastTime = _userFloodTime.get(seat);
			int lastCounter = _userFloodNumber.get(seat);
			long currentTime = System.currentTimeMillis();
			boolean passed = true;
			if ( currentTime >= (lastTime+1000))
			{
				lastTime = currentTime;
				lastCounter = 1;
			}
			else
			{
				lastCounter++;
				if(lastCounter > 5)
				{
					passed = false;
				}
			}
			_userFloodTime.put(seat, lastTime);
			_userFloodNumber.put(seat, lastCounter);
			if (passed)
			{
				SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
				ISFSObject sendObj = new SFSObject();
				sendObj.putInt(ConstantClass.SENDER_ID, playerID);
				sendObj.putInt(ConstantClass.SEAT_INDEX, seat);
				sendObj.putUtfString(ConstantClass.MESSAGE, msg);
				sendObj.putUtfString(ConstantClass.NICKNAME, _userNickname.get(seat));
				sendObj.putUtfString(ConstantClass.TIME, dateFormat.format(new Date()));
				sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
				this.send(ConstantClass.GET_ROOM_MESSAGE, sendObj, GetUserList(-1));
			}
			else
			{
				KickUser(user, "UserSendMessage : Flooding.", 0);
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserSendMessage : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void GetCurrentGameInfo(ISFSObject data) throws Exception
	{
		String userName = data.getUtfString(ConstantClass.USER_ID);
		User user = this.getParentRoom().getUserByName(userName);
		if (user == null)
		{
			LogMessage(ExtensionLogLevel.WARN, String.format("GetCurrentGameInfo : User %s not found.", userName));
			return;
		}
		try
		{
			int playerID = Integer.parseInt(userName);
			int seat = getUserSeat(playerID);
			int missionid = get_currentMission();
			ISFSObject sendObj = new SFSObject();
			sendObj.putInt(ConstantClass.MY_SEAT, seat);
			if (seat >= 0)
			{
				if(_userStatus.get(seat) == UserState.DONE)
				{
					ArrayList<String> getDeck = new ArrayList<String>();
					ArrayList<Integer> usermakecard = _userMakeCard.get(playerID);
				    for (int x = 0; x < usermakecard.size(); x++)
				    {
				    	short suitnum = (short) ((usermakecard.get(x)+13)/13);
				    	short ranknum = (short) ((usermakecard.get(x)+13)%13);
				    	Card card = new Card(suitnum,ranknum);
				    	getDeck.add(card.toString());
				    }
				    sendObj.putBool(ConstantClass.IS_PLAYING_GAME, true);
					sendObj.putUtfStringArray(ConstantClass.MY_CARDS, getDeck);
				} 
				else 
				{
					if(_userCard.containsKey(seat))
					{
						sendObj.putUtfStringArray(ConstantClass.MY_CARDS, _userCard.get(seat));
					}
					else
					{
						sendObj.putUtfStringArray(ConstantClass.MY_CARDS, new ArrayList<String>());
					}
					sendObj.putBool(ConstantClass.IS_PLAYING_GAME, false);
				}
			}
			else
			{
				sendObj.putBool(ConstantClass.IS_PLAYING_GAME, false);
				sendObj.putUtfStringArray(ConstantClass.MY_CARDS, new ArrayList<String>());
			}
			
			if (_isVip)
			{
				if (playerID == _creator)
				{
					sendObj.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
				}
				else
				{
					sendObj.putBool(ConstantClass.CAN_INVITE_PLAYER, false);
				}
			}
			else
			{
				sendObj.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
			}
			
			
			sendObj.putBool(ConstantClass.FAST_ROOM, _isFastRoom);
			sendObj.putBool(ConstantClass.IS_VIP, _isVip);
			sendObj.putBool(ConstantClass.HAS_MISSION, _hasMission);
			sendObj.putInt(ConstantClass.CURRENT_MISSION, missionid);
			sendObj.putInt(ConstantClass.MISSION_OBTAIN, GetMissionMultiple(missionid));
			sendObj.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
			
			if(_currentState == RoomState.GAME_PLAY)
			{
				sendObj.putInt(ConstantClass.TIME, _loopCountDown);
			}
			else
			{
				sendObj.putInt(ConstantClass.TIME,-1);
			}
			
			sendObj.putUtfStringArray(ConstantClass.LAST_TOTAL_RESULT, lastTotalResult);
			sendObj.putInt(ConstantClass.LAST_RESULT_ID, lastResultID);
			if (lastResultID == 1)
			{
				sendObj.putInt(ConstantClass.DRAGON_MODIFIER, lastDragonModifier);
				sendObj.putUtfStringArray(ConstantClass.DRAGON_CARDS, lastWinCard);
			}
			else if (lastResultID == 2)
			{
				sendObj.putUtfStringArray(ConstantClass.INSTANTWIN_CARDS, lastWinCard);
			}
			
			sendObj.putBool(ConstantClass.IS_HOT_TIME_EVENT, _isHotTime);
			sendObj.putInt(ConstantClass.HOT_TIME_EVENT_TARGET, _hotTimeTarget);
			sendObj.putInt(ConstantClass.HOT_TIME_EVENT_CURRENT, _userHotTimePlay.get(seat));
			sendObj.putInt(ConstantClass.HOT_TIME_MISSION_MULTI, _hotTimeMissionMulti);
			sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
			this.send(ConstantClass.CURRENT_GAME_INFO, sendObj, user);
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("GetCurrentGameInfo : %s.", ErrorClass.StackTraceToString(e)));	
		}
	}
	
	private void GetCurrentRoomInfo(ISFSObject data) throws Exception
	{
		try
		{
			String userName = data.getUtfString(ConstantClass.USER_ID);
			User user = this.getParentRoom().getUserByName(userName);
			if (user == null)
			{
				LogMessage(ExtensionLogLevel.WARN, String.format("GetCurrentRoomInfo : User %s not found.", userName));
				return;
			}
			int playerID = Integer.parseInt(user.getName());
			ISFSObject seatsInfo = new SFSObject();
			for ( int i = 0; i < 4; i++)
			{
				ISFSObject userData = new SFSObject();
				if ( _userSeat.get(i) >= 0)
				{
					userData.putInt(ConstantClass.USER_ID, _userSeat.get(i));
					userData.putInt(ConstantClass.GENDER, _userGender.get(i));
					userData.putLong(ConstantClass.COINS, _userCoin.get(i));
					userData.putLong(ConstantClass.EXP, _userExp.get(i));
					userData.putInt(ConstantClass.LEVEL, GetLevel(_userExp.get(i)));
					userData.putInt(ConstantClass.DECO_ID, _userDeco.get(i));
					userData.putInt(ConstantClass.VIP_GRADE, _userVipGrade.get(i));
					userData.putInt(ConstantClass.LEAGUE_GRADE, _userLeagueGrade.get(i));
					userData.putInt(ConstantClass.LEAGUE_DIVISION, _userLeagueDivision.get(i));
					userData.putUtfString(ConstantClass.NICKNAME, _userNickname.get(i));
					if ( _userStatus.get(i) == UserState.WAIT_NEXT_GAME)
					{
						userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, true);
					}
					else
					{
						userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, false);
					}
					if (_isVip)
					{
						if (_userSeat.get(i) == _creator)
						{
							userData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
						}
						else
						{
							userData.putBool(ConstantClass.CAN_INVITE_PLAYER, false); 
						}
					}
					else
					{
						userData.putBool(ConstantClass.CAN_INVITE_PLAYER, true);
					}
				}
				else
				{
					userData.putInt(ConstantClass.USER_ID, -1);
					userData.putInt(ConstantClass.GENDER, 0);
					userData.putLong(ConstantClass.COINS, 0);
					userData.putLong(ConstantClass.EXP, 0);
					userData.putInt(ConstantClass.LEVEL, 1);
					userData.putInt(ConstantClass.DECO_ID, 0);
					userData.putInt(ConstantClass.VIP_GRADE, 0);
					userData.putInt(ConstantClass.LEAGUE_GRADE, 0);
					userData.putInt(ConstantClass.LEAGUE_DIVISION, 1);
					userData.putUtfString(ConstantClass.NICKNAME, "-");
					userData.putBool(ConstantClass.IS_WAIT_NEXT_GAME, false);
					userData.putBool(ConstantClass.CAN_INVITE_PLAYER, false);
				}
				seatsInfo.putSFSObject(Integer.toString(i), userData);
			}
			seatsInfo.putLong(ConstantClass.BASE_BET, _baseBet);
			seatsInfo.putUtfString(ConstantClass.ROOM_KEY, _roomName);
			seatsInfo.putBool(ConstantClass.HAS_MISSION, _hasMission);
			seatsInfo.putInt(ConstantClass.MISSION_ID, _currentMission);
			seatsInfo.putBool(ConstantClass.IS_VIP, _isVip);
			seatsInfo.putBool(ConstantClass.FAST_ROOM, _isFastRoom);
			seatsInfo.putBool(ConstantClass.IS_RECONNECT, false);
			seatsInfo.putInt(ConstantClass.PLAYING_STATUS, getCurrentState());
			seatsInfo.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
			seatsInfo.putBool(ConstantClass.IS_TOURNAMENT, false);
			seatsInfo.putBool(ConstantClass.IS_HOT_TIME_EVENT, _isHotTime);
			seatsInfo.putInt(ConstantClass.HOT_TIME_EVENT_TARGET, _hotTimeTarget);
			seatsInfo.putInt(ConstantClass.HOT_TIME_EVENT_CURRENT, _userHotTimePlay.get(getUserSeat(playerID)));
			seatsInfo.putInt(ConstantClass.HOT_TIME_MISSION_MULTI, _hotTimeMissionMulti);
			this.send(ConstantClass.CURRENT_ROOM_INFO, seatsInfo, user);
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("GetCurrentGameInfo : %s.", ErrorClass.StackTraceToString(e)));	
		}
	}
	
	private void UserResultReady(ISFSObject data) throws Exception
	{
		try
		{
			String userName = data.getUtfString(ConstantClass.USER_ID);
			User sender = this.getParentRoom().getUserByName(userName);
			ArrayList<Integer> usermakecard =  (ArrayList<Integer>) data.getIntArray(ConstantClass.USER_MAKE_DECK);
			if (sender == null)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("UserResultReady : User %s not found.", userName));
				return;
			}
			int pid = Integer.parseInt(userName);
			int seat = getUserSeat(pid);
			if ((_loopIndex >= 125) && (_loopIndex < 700))
			{
				UserState playerStatus = _userStatus.get(seat);
				// Check Player Status
				if ( (playerStatus == UserState.EMPTY) || (playerStatus == UserState.WAIT_NEXT_GAME))
				{
					LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %d with status %s Send ResultReady.", pid, playerStatus.toString()));
					return;
				}
				// if a player request twice, just change his card information.
				if((playerStatus == UserState.DONE) || (playerStatus == UserState.DISCONNECTED_DONE))
				{
					_userStatus.put(seat,  UserState.DONE);
					_userMakeCard.put(pid,usermakecard);
	                return;
				}
				_readycount++;
				//make All Player's Card List
				_userStatus.put(seat,  UserState.DONE);
				_userMakeCard.put(pid,usermakecard);
		
				// Reply Sender make card received.
				this.send(ConstantClass.ACCEPT_RESULT_DONE, new SFSObject(), sender);
				
				String s = "";
			    for (int x = 0; x < usermakecard.size(); x++)
			    {
			    	short suitnum = (short) ((usermakecard.get(x)+13)/13);
			    	short ranknum = (short) ((usermakecard.get(x)+13)%13);
			    	Card card = new Card(suitnum,ranknum);
			    	s += card.toString() + ",";
			    }
			    LogMessage(String.format("======== Player : %d UserResultReady Card : %s========", pid, s));
			    
				if(_readycount == _totalPlayer)
				{
					if (_loopIndex < 600)
					{
						_loopCountDown = 0;
						_loopIndex = 600;
					}
				}
				else
				{
					ArrayList<String> Arr_UserReady = new ArrayList<String>();
					//Send Client All User's done info
					for(int i = 0; i < 4; i++)
					{
						int pidx = _userSeat.get(i);
						if(pidx == pid)
						{
							Arr_UserReady.add(String.format("%d:1",pidx));	
						} 
						else 
						{
							if((_userStatus.get(i) == UserState.DONE) || (_userStatus.get(i) == UserState.DISCONNECTED_DONE))
							{
								Arr_UserReady.add(String.format("%d:1",pidx));
							}
							else if(_userStatus.get(i) == UserState.WAIT_NEXT_GAME)
							{	
								Arr_UserReady.add("-1:0");
							}
							else 
							{
								Arr_UserReady.add(String.format("%d:0",pidx));
							}
						}
					}
					
					ISFSObject params = new SFSObject();
					params.putUtfStringArray(ConstantClass.READY_PLAYER, Arr_UserReady);
					params.putBool(ConstantClass.IS_TOURNAMENT, false);
					this.send(ConstantClass.READY_INFO, params,GetUserListExceptWait());
				}
			}
			else
			{
				// For Test
				long diffTime = System.currentTimeMillis() - _StartResultTime;
				trace(String.format("LATERESULT:%d:%d:%d:%s:%d", diffTime, pid, _loopIndex, _roomName, _loopIndex));
				// For Test
			}
		}
		catch(Exception ex)
		{
		    LogMessage(ExtensionLogLevel.ERROR, String.format("UserResultReady : %s.", ErrorClass.StackTraceToString(ex)));
		}
	}
	
	private void UserTimeoutDeck(ISFSObject data) throws Exception
	{
		try
		{
			String userName = data.getUtfString(ConstantClass.USER_ID);
			User sender = this.getParentRoom().getUserByName(userName);
			ArrayList<Integer> usermakecard = (ArrayList<Integer>) data.getIntArray(ConstantClass.USER_MAKE_DECK);
			if (sender == null)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("UserTimeoutDeck : User %s not found.", userName));
				return;
			}
			
			int pid = Integer.parseInt(userName);
			int seat = getUserSeat(pid);
			UserState playerStatus = _userStatus.get(seat);
			if ((_loopIndex >= 125) && (_loopIndex < 700))
			{
				// Check Player Status
				if ( (playerStatus == UserState.EMPTY) || (playerStatus == UserState.WAIT_NEXT_GAME))
				{
					LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %d with status %s Send GameFinish.", pid, playerStatus.toString()));
					return;
				}
				// if a player request twice, just change his card information.
				if((playerStatus == UserState.DONE) || (playerStatus == UserState.DISCONNECTED_DONE))
				{
					_userStatus.put(seat,  UserState.DONE);
					_userMakeCard.put(pid,usermakecard);
	                return;
				}
				
				//make All Player's Card List
				_readycount++;
				_userStatus.put(seat,  UserState.DONE);
				_userMakeCard.put(pid,usermakecard);
				if(_readycount == _totalPlayer)
				{
					if (_loopIndex < 600)
					{
						_loopCountDown = 0;
						_loopIndex = 600;
					}
				}
			}
			else
			{
				// For Test
				long diffTime = System.currentTimeMillis() - _StartResultTime;
				trace(String.format("LATETIMEOUT:%d:%d:%s:%d", diffTime, pid, _roomName, _loopIndex));
				// For Test
				//LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %d with status %s Send GameFinish when in LoopIndex %d.", pid, playerStatus.toString(), _loopIndex));
			}
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserTimeoutDeck : %s.", ErrorClass.StackTraceToString(e)));
		}
	}

	private void UserResortDeck(ISFSObject data) throws Exception
	{
		try
		{
			String userName = data.getUtfString(ConstantClass.USER_ID);
			User sender = this.getParentRoom().getUserByName(userName);
			if (sender == null)
			{
				LogMessage(ExtensionLogLevel.WARN, String.format("UserResortDeck : User %s not found.", userName));
				return;
			}
			
			//Send User's original card again
			int pid = Integer.parseInt(userName);
			int UserSeatNum = getUserSeat(pid);

			//COUNTDOWN TIME CHECK 
			if((_loopCountDown > 5) && (_currentState == RoomState.GAME_PLAY))
			{
				//User Status Done Check
				if(_userStatus.get(UserSeatNum) == UserState.DONE)
				{
					_readycount--;
					_userStatus.put(UserSeatNum,  UserState.PLAYING);
			
					ArrayList<Integer> getDeck = _userMakeCard.get(pid);
					ArrayList<String> sendDeck = new ArrayList<String>();
					
				    for (int x = 0; x < getDeck.size(); x++)
				    {
				    	short suitnum = (short) ((getDeck.get(x)+13)/13);
				    	short ranknum = (short) ((getDeck.get(x)+13)%13);
				    	Card card = new Card(suitnum,ranknum);
				    	sendDeck.add(card.toString());
				    }
					
					ISFSObject params = new SFSObject();
					params.putUtfStringArray(ConstantClass.CARDS, sendDeck);
					params.putBool(ConstantClass.IS_TOURNAMENT, false);
					this.send(ConstantClass.RESORT_CARD, params, sender);
					
					LogMessage(String.format("Resort Deck : Seat %d with PlayerID %d, Deck : %s.",UserSeatNum, pid, sendDeck.toString()));
					
					ArrayList<String> Arr_UserReady = new ArrayList<String>();
					//Send Client All User's done info
					for(int i = 0; i < 4; i++)
					{
						int pidx = _userSeat.get(i);
						if(pidx == pid)
						{
							Arr_UserReady.add(String.format("%d:0",pidx));	
						} 
						else 
						{
							if((_userStatus.get(i) == UserState.DONE) || (_userStatus.get(i) == UserState.DISCONNECTED_DONE))
							{
								Arr_UserReady.add(String.format("%d:1",pidx));
							}
							else if(_userStatus.get(i) == UserState.WAIT_NEXT_GAME)
							{	
								Arr_UserReady.add("-1:0");
							}
							else 
							{
								Arr_UserReady.add(String.format("%d:0",pidx));
							}
						}
					}
					ISFSObject params1 = new SFSObject();
					params1.putUtfStringArray(ConstantClass.READY_PLAYER, Arr_UserReady);
					params1.putBool(ConstantClass.IS_TOURNAMENT, false);
					this.send(ConstantClass.READY_INFO, params1, GetUserListExceptWait());
				}
			}
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("UserResortDeck : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	// Room Functions
	
	private void KickUser(User user, String reason, int delay)
	{
		try
		{
			if ( user != null)
			{
				LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %s kicked, reason : %s", user.getName(), reason));
				this.getApi().kickUser(user, null, reason, delay);
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("KickUser : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	private void ForceAllUserOut()
	{
		try
		{
			List<User> userList = this.getParentRoom().getUserList();
			for(User user:userList)
			{
				this.getApi().kickUser(user, null, "Room Force Out.", 1);
			}
		}
		catch (Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("ForceAllUserOut : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	public void BankrouptUserKick() throws Exception
	{
		for(int i = 0; i < 4; i++)
		{
			try
			{
				int pidx = _userSeat.get(i);
				if(pidx != -1)
				{
					int seat = getUserSeat(pidx);
					long coin = _userCoin.get(seat);
					//Bankroupt
					if(coin <= 0)
					{	
						HandleUserLeave(pidx, seat, "Bankrupt");
						User user = this.getParentRoom().getUserByName(Integer.toString(pidx));
						if (user != null)
						{
							this.send(ConstantClass.BANKRUPT_LEAVE, new SFSObject(), user);
							//this.getApi().kickUser(user, null, "Bankrupt.", 1);
						}
					}
				}
			}
			catch(Exception e)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("BankrouptUserKick : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	public void addResultToDatabase(int pidx,	long exp, short win, long resultCoin, int seat)
	{
		threadPool.execute(new UpdatePlayerToDatabase(pidx,	exp, win, resultCoin));
	}
	
	private boolean checkReconnect(int playerID) throws Exception
	{
		boolean isReconnect = false;
		for(int i = 0; i < 4; i++)
		{
			if (_userSeat.get(i) == playerID)
			{
				isReconnect = true;
				if (!_isPlaying)
				//if ( (_currentState == RoomState.IDLE) || (_currentState == RoomState.WAIT_START_GAME) || (_currentState == RoomState.COUNTDOWN_START_GAME))
				{
					_userStatus.put(i, UserState.PLAYING);
				}
				else
				{
					if (_userStatus.get(i) == UserState.DISCONNECTED)
					{
						_userStatus.put(i, UserState.PLAYING);
					}
					else if (_userStatus.get(i) == UserState.DISCONNECTED_DONE)
					{
						_userStatus.put(i, UserState.DONE);
					}
					else
					{
						LogMessage(ExtensionLogLevel.ERROR, String.format("checkReconnect : PlayerID %d status %s while reconnecting when game is Playing.", playerID, _userStatus.get(i).toString()));
					}
				}
				break;
			}
		}
		return isReconnect;
	}
	
	private ISFSObject GetSeatsInfo()
	{
		ISFSObject data = new SFSObject();
		data.putInt(ConstantClass.ROOM_KEY, Integer.parseInt(_roomName));
		data.putInt(ConstantClass.BASE_BET, _betGrade);
		data.putInt(ConstantClass.ROOM_CREATOR, _creator);
		data.putInt(ConstantClass.USER_SEAT_1, _userSeat.get(0));
		data.putInt(ConstantClass.USER_SEAT_2, _userSeat.get(1));
		data.putInt(ConstantClass.USER_SEAT_3, _userSeat.get(2));
		data.putInt(ConstantClass.USER_SEAT_4, _userSeat.get(3));
		data.putInt(ConstantClass.GENDER_SEAT_1, _userGender.get(0));
		data.putInt(ConstantClass.GENDER_SEAT_2, _userGender.get(1));
		data.putInt(ConstantClass.GENDER_SEAT_3, _userGender.get(2));
		data.putInt(ConstantClass.GENDER_SEAT_4, _userGender.get(3));
		data.putInt(ConstantClass.LEVEL_SEAT_1, GetLevel(_userExp.get(0)));
		data.putInt(ConstantClass.LEVEL_SEAT_2, GetLevel(_userExp.get(1)));
		data.putInt(ConstantClass.LEVEL_SEAT_3, GetLevel(_userExp.get(2)));
		data.putInt(ConstantClass.LEVEL_SEAT_4, GetLevel(_userExp.get(3)));
		data.putInt(ConstantClass.VIP_GRADE_1, _userVipGrade.get(0));
		data.putInt(ConstantClass.VIP_GRADE_2, _userVipGrade.get(1));
		data.putInt(ConstantClass.VIP_GRADE_3, _userVipGrade.get(2));
		data.putInt(ConstantClass.VIP_GRADE_4, _userVipGrade.get(3));
		data.putInt(ConstantClass.LEAGUE_GRADE_1, _userLeagueGrade.get(0));
		data.putInt(ConstantClass.LEAGUE_GRADE_2, _userLeagueGrade.get(1));
		data.putInt(ConstantClass.LEAGUE_GRADE_3, _userLeagueGrade.get(2));
		data.putInt(ConstantClass.LEAGUE_GRADE_4, _userLeagueGrade.get(3));
		data.putInt(ConstantClass.LEAGUE_DIVISION_1, _userLeagueDivision.get(0));
		data.putInt(ConstantClass.LEAGUE_DIVISION_2, _userLeagueDivision.get(1));
		data.putInt(ConstantClass.LEAGUE_DIVISION_3, _userLeagueDivision.get(2));
		data.putInt(ConstantClass.LEAGUE_DIVISION_4, _userLeagueDivision.get(3));
		data.putUtfString(ConstantClass.NICKNAME_SEAT_1, _userNickname.get(0));
		data.putUtfString(ConstantClass.NICKNAME_SEAT_2, _userNickname.get(1));
		data.putUtfString(ConstantClass.NICKNAME_SEAT_3, _userNickname.get(2));
		data.putUtfString(ConstantClass.NICKNAME_SEAT_4, _userNickname.get(3));
		data.putBool(ConstantClass.HAS_MISSION, _hasMission);
		data.putBool(ConstantClass.FAST_ROOM, _isFastRoom);
		data.putBool(ConstantClass.IS_VIP, _isVip);
		data.putBool(ConstantClass.IS_ROOM_PLAYING, _isPlaying);
		data.putBool(ConstantClass.IS_TOURNAMENT, false);
		return data;
	}
	
	public void AddJackpot(float tax)
	{
		threadPool.execute(new AddJackpotFromTax(tax));
	}
	
	/*
	 * Game, Cards Functions
	 */

	//int test = 0;
	private void ShuffleCard() throws Exception
	{
		_match++;
		_userCard.clear();
		
		ArrayList<Card> cards = new ArrayList<Card>();
		ArrayList<String> deck1 = new ArrayList<String>();
		ArrayList<String> deck2 = new ArrayList<String>();
		ArrayList<String> deck3 = new ArrayList<String>();
		ArrayList<String> deck4 = new ArrayList<String>();
		
		for (short a=1; a<=4; a++)
        {
            for (short b=0; b<=12; b++)
            {
            	cards.add( new Card(a,b) );
            }
        }

	    Collections.shuffle(cards);
	    
	    int di = 0;
	    for (Card c : cards)
	    {
	    	switch(di % 4)
	    	{
		    	case 0:
		    		deck1.add(c.toString());
		    		break;
		    	case 1:
		    		deck2.add(c.toString());
		    		break;
		    	case 2:
		    		deck3.add(c.toString());
		    		break;
		    	case 3:
		    		deck4.add(c.toString());
		    		break;
	    	};
	    	//LogMessage(String.format("Card : %s", c.toString()));
	    	di++;
	    }
	    
		/* 
		//Check Card
		for(Entry<Integer, ArrayList<String>> e : _userCard.entrySet())
		{
			Integer key = e.getKey();
			ArrayList<String> value = e.getValue();
		    for (String s : value){
		    	LogMessage(String.format("Deck Num : %d, Card : %s", key,s));
		    }	
	    }
	    */
	    
	    
	   /*
	     // Test
	    test++;
	    if(test == 1) // dagon
	    {
	    	cards.clear();
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }

			deck1.clear();
			
			deck1.add(cards.get(37).toString());
			deck1.add(cards.get(35).toString());
			deck1.add(cards.get(43).toString());
			deck1.add(cards.get(9).toString());
			deck1.add(cards.get(8).toString());
			deck1.add(cards.get(7).toString());
			deck1.add(cards.get(6).toString());
			deck1.add(cards.get(5).toString());
			deck1.add(cards.get(19).toString());
			deck1.add(cards.get(18).toString());
			deck1.add(cards.get(17).toString());
			deck1.add(cards.get(16).toString());
			deck1.add(cards.get(15).toString());
	    }
	    else  if(test == 99) // special 4card etc
	    {
	    	cards.clear();
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }

			deck1.clear();
			
			deck1.add(cards.get(12).toString());
			deck1.add(cards.get(25).toString());
			deck1.add(cards.get(38).toString());
			deck1.add(cards.get(51).toString());
			deck1.add(cards.get(2).toString());
			deck1.add(cards.get(10).toString());
			deck1.add(cards.get(23).toString());
			deck1.add(cards.get(36).toString());
			deck1.add(cards.get(4).toString());
			deck1.add(cards.get(17).toString());
			deck1.add(cards.get(9).toString());
			deck1.add(cards.get(22).toString());
			deck1.add(cards.get(1).toString());
			
			deck2.clear();
			
			deck2.add(cards.get(5).toString());
			deck2.add(cards.get(8).toString());
			deck2.add(cards.get(14).toString());
			deck2.add(cards.get(15).toString());
			deck2.add(cards.get(20).toString());
			deck2.add(cards.get(26).toString());
			deck2.add(cards.get(32).toString());
			deck2.add(cards.get(34).toString());
			deck2.add(cards.get(40).toString());
			deck2.add(cards.get(41).toString());
			deck2.add(cards.get(42).toString());
			deck2.add(cards.get(46).toString());
			deck2.add(cards.get(47).toString());
			
			deck3.clear();
			
			deck3.add(cards.get(0).toString());
			deck3.add(cards.get(3).toString());
			deck3.add(cards.get(6).toString());
			deck3.add(cards.get(11).toString());
			deck3.add(cards.get(16).toString());
			deck3.add(cards.get(21).toString());
			deck3.add(cards.get(27).toString());
			deck3.add(cards.get(28).toString());
			deck3.add(cards.get(30).toString());
			deck3.add(cards.get(33).toString());
			deck3.add(cards.get(44).toString());
			deck3.add(cards.get(45).toString());
			deck3.add(cards.get(49).toString());
			
			deck4.clear();
			
			deck4.add(cards.get(7).toString());
			deck4.add(cards.get(13).toString());
			deck4.add(cards.get(18).toString());
			deck4.add(cards.get(19).toString());
			deck4.add(cards.get(24).toString());
			deck4.add(cards.get(29).toString());
			deck4.add(cards.get(31).toString());
			deck4.add(cards.get(35).toString());
			deck4.add(cards.get(37).toString());
			deck4.add(cards.get(39).toString());
			deck4.add(cards.get(43).toString());
			deck4.add(cards.get(48).toString());
			deck4.add(cards.get(50).toString());
			
	    }
	    else if (test == 2) // 5pair 1 tree
	    {
	    	cards.clear();
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }

			deck1.clear();
			
			deck1.add(cards.get(0).toString());
			deck1.add(cards.get(13).toString());
			deck1.add(cards.get(1).toString());
			deck1.add(cards.get(14).toString());
			deck1.add(cards.get(28).toString());
			deck1.add(cards.get(41).toString());
			deck1.add(cards.get(30).toString());
			deck1.add(cards.get(43).toString());
			deck1.add(cards.get(37).toString());
			deck1.add(cards.get(50).toString());
			deck1.add(cards.get(38).toString());
			deck1.add(cards.get(51).toString());
			//deck1.add(cards.get(6).toString());//SIX
			deck1.add(cards.get(39).toString());//FIVE TRIPLE
	    }
	    else if (test == 4) //Three Straight
	    {
	    	cards.clear();
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }
			
			deck1.clear();
	    	
			deck1.add(cards.get(0).toString());
			deck1.add(cards.get(1).toString());
			deck1.add(cards.get(2).toString());
			deck1.add(cards.get(15).toString());
			deck1.add(cards.get(16).toString());
			deck1.add(cards.get(17).toString());
			deck1.add(cards.get(5).toString());
			deck1.add(cards.get(6).toString());
			deck1.add(cards.get(32).toString());
			deck1.add(cards.get(33).toString());
			deck1.add(cards.get(47).toString());
			deck1.add(cards.get(48).toString());
			deck1.add(cards.get(49).toString());
	    }
	    else if (test == 3) // gramd dragon
	    {
	    	cards.clear();
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }

			deck1.clear();
			
			deck1.add(cards.get(0).toString());
			deck1.add(cards.get(1).toString());
			deck1.add(cards.get(2).toString());
			deck1.add(cards.get(3).toString());
			deck1.add(cards.get(4).toString());
			deck1.add(cards.get(5).toString());
			deck1.add(cards.get(6).toString());
			deck1.add(cards.get(7).toString());
			deck1.add(cards.get(8).toString());
			deck1.add(cards.get(9).toString());
			deck1.add(cards.get(10).toString());
			deck1.add(cards.get(11).toString());
			deck1.add(cards.get(12).toString());
	    }
	    else if (test > 3)
	    {
	    	test = 0;
	    }
		*/
	    /*
		// Dragon Test
	    if(_match%2 == 1)
	    {
			// Dragon Test
			cards.clear();   
			
			for (short a=1; a<=4; a++)
	        {
	            for (short b=0; b<=12; b++)
	             {
	               cards.add( new Card(a,b) );
	             }
	        }

			deck1.clear();
			
			deck1.add(cards.get(0).toString());
			deck1.add(cards.get(1).toString());
			deck1.add(cards.get(2).toString());
			deck1.add(cards.get(3).toString());
			deck1.add(cards.get(4).toString());
			deck1.add(cards.get(5).toString());
			deck1.add(cards.get(6).toString());
			deck1.add(cards.get(33).toString());
			deck1.add(cards.get(34).toString());
			deck1.add(cards.get(35).toString());
			deck1.add(cards.get(36).toString());
			deck1.add(cards.get(37).toString());
			deck1.add(cards.get(38).toString());
	    	
	    }
		
		deck3.clear();
		
		for(int i = 13; i < 26; i++)
		{
			deck3.add(cards.get(i).toString());
		}
		
		// End Dragon Test
		// Instant Win Test Start
		cards.clear();
		
		for (short a=1; a<=4; a++)
        {
            for (short b=0; b<=12; b++)
             {
               cards.add( new Card(a,b) );
             }
        }

		deck1.clear();
		
		deck1.add(cards.get(0).toString());
		deck1.add(cards.get(13).toString());
		deck1.add(cards.get(1).toString());
		deck1.add(cards.get(14).toString());
		deck1.add(cards.get(28).toString());
		deck1.add(cards.get(41).toString());
		deck1.add(cards.get(30).toString());
		deck1.add(cards.get(43).toString());
		deck1.add(cards.get(37).toString());
		deck1.add(cards.get(50).toString());
		deck1.add(cards.get(38).toString());
		deck1.add(cards.get(51).toString());
		deck1.add(cards.get(6).toString());//SIX
		//deck1.add(cards.get(39).toString());//FIVE TRIPLE

		//Three Straight
		deck1.add(cards.get(0).toString());
		deck1.add(cards.get(1).toString());
		deck1.add(cards.get(2).toString());
		deck1.add(cards.get(15).toString());
		deck1.add(cards.get(16).toString());
		deck1.add(cards.get(17).toString());
		deck1.add(cards.get(5).toString());
		deck1.add(cards.get(6).toString());
		deck1.add(cards.get(32).toString());
		deck1.add(cards.get(33).toString());
		deck1.add(cards.get(47).toString());
		deck1.add(cards.get(48).toString());
		deck1.add(cards.get(49).toString());
		
		//Three Flush
		deck1.add(cards.get(0).toString());
		deck1.add(cards.get(1).toString());
		deck1.add(cards.get(5).toString());
		deck1.add(cards.get(13).toString());
		deck1.add(cards.get(14).toString());
		deck1.add(cards.get(15).toString());
		deck1.add(cards.get(18).toString());
		deck1.add(cards.get(19).toString());
		deck1.add(cards.get(30).toString());
		deck1.add(cards.get(31).toString());
		deck1.add(cards.get(34).toString());
		deck1.add(cards.get(37).toString());
		deck1.add(cards.get(38).toString());
		
		//Stright test salah susun
		deck1.add(cards.get(26).toString());
		deck1.add(cards.get(1).toString());
		deck1.add(cards.get(2).toString());
		deck1.add(cards.get(3).toString());
		deck1.add(cards.get(4).toString());
		deck1.add(cards.get(9).toString());
		deck1.add(cards.get(10).toString());
		deck1.add(cards.get(11).toString());
		deck1.add(cards.get(12).toString());
		deck1.add(cards.get(13).toString());
		deck1.add(cards.get(6).toString());
		deck1.add(cards.get(7).toString());
		deck1.add(cards.get(25).toString());
		
		// Instant Win Test End
		*/
	    
	    
	    
	    
	    
	    
	   		
		_userCard.put(0, deck1);
		_userCard.put(1, deck2);
		_userCard.put(2, deck3);
		_userCard.put(3, deck4);
		
		//Send All User Mission..
		try
		{
			boolean cekHotTime = (boolean)this.getParentZone().getExtension().handleInternalMessage("IsHotTimeEvent", null);
			if (cekHotTime)
			{
				_isHotTime = true;
			}
			MissionSetup();
		}
		catch(Exception ex)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("mission : %s", ErrorClass.StackTraceToString(ex)));
		}
		
		
		
		
		
		
		for (int i = 0; i < 4; i++)
		{
			int playerID = _userSeat.get(i);
			if (playerID >= 0)
			{
				ArrayList<String> sendDeck = _userCard.get(i);
				User sendUser = this.getParentRoom().getUserByName(Integer.toString(playerID));
				UserState playerState = _userStatus.get(i);
				
				if ( sendUser != null)
				{
					if (((playerState == UserState.PLAYING) || (playerState == UserState.DISCONNECTED)))
					{
						ISFSObject params = new SFSObject();
						params.putUtfStringArray(ConstantClass.CARDS, sendDeck);
						params.putBool(ConstantClass.IS_TOURNAMENT, false);
						params.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
						this.send(ConstantClass.SHUFFLE_CARD, params, sendUser);
						LogMessage(String.format("Seat %d with PlayerID %d, State : %s, Deck : %s.", i, playerID, playerState.toString(), sendDeck.toString()));
					}
					else
					{
						LogMessage(String.format("Check ghost room , User PlayerID=%d , Seat = %d , Status %s ", playerID, i, _userStatus.get(i).toString()));
					}
				}
				else
				{
					LogMessage(String.format("sendUser is null, seat no = %d ",i));
				}
			}
			else
			{
				LogMessage(String.format("Seat %d No Player, Deck : %s.", i, _userCard.get(i).toString()));
				_userCard.get(i).clear();
			}
		}
		
		int _tempSavedMission = 0;
		if ( _hasMission)
		{
			_tempSavedMission = _currentMission;
		}
		
		// Log
		String stringRoomType = "NORMAL";
		if (_isFastRoom)
		{
			stringRoomType = "FAST";
		}
		int logRoomType = 1;
		if (_isFastRoom)
		{
			logRoomType = 0;
		}
		
		DBManager dm = DBManager.getInstance();
		_roomID = String.format("%s-%d-%d-%s-%d", _createDate, logRoomType, _baseBet, _roomName, _match);
		// TEMP : isVip in log filled in HiddenRoom for now
		Integer logret = dm.InsertLogInRoomPlay(_roomID, stringRoomType,String.valueOf(_tempSavedMission), (int)_baseBet, _userSeat.get(0), _userSeat.get(1), _userSeat.get(2), _userSeat.get(3), _isVip);
		if(logret != 0)
		{
			LogMessage(ExtensionLogLevel.ERROR, "ShuffleCard : SP InsertLogInRoomPlay Error.");
		}
				
		// Hot Time Event
		try
		{
			boolean cekHotTime = (boolean)this.getParentZone().getExtension().handleInternalMessage("IsHotTimeEvent", null);
			if (cekHotTime)
			{
				ISFSObject hotTimeData = (ISFSObject) this.getParentZone().getExtension().handleInternalMessage("GetHotTimeEventData", null);
				int taxReduce = hotTimeData.getInt(ConstantClass.HOT_TIME_EVENT_TAX_REDUCE);
				int RewardTimes = hotTimeData.getInt(ConstantClass.HOT_TIME_EVENT_REWARD_TIMES);
				int targetType = hotTimeData.getInt(ConstantClass.HOT_TIME_EVENT_TYPE_TARGET);
	
				
				_hotTimeTarget = RewardTimes;
				for (int i = 0; i < 4; i++)
				{
					int playerID = _userSeat.get(i);
					if (playerID >= 0)
					{
						if (targetType == 0)
						{
							_userTaxBuff.put(i, 1f - (taxReduce / 100.0f));
							_userHotTimePlay.put(i, _userHotTimePlay.get(i) + 1);
						}
						else if (targetType == 1)
						{
							if (_userType.get(i).equals("Guest"))
							{
								_userHotTimePlay.put(i, 0);
								_userTaxBuff.put(i, 1f);
							}
							else
							{
								_userTaxBuff.put(i, 1f - (taxReduce / 100.0f));
								_userHotTimePlay.put(i, _userHotTimePlay.get(i) + 1);
							}
						}
					}
				}
				if (_isHotTime && (RewardTimes > 0))
				{
					for (int i = 0; i < 4; i++)
					{
						int playerID = _userSeat.get(i);
						if (playerID >= 0)
						{
							User sendUser = this.getParentRoom().getUserByName(Integer.toString(playerID));
							UserState playerState = _userStatus.get(i);
							if ( sendUser != null)
							{
								if ((_userSeat.get(i) >= 0) && ((playerState == UserState.PLAYING) || (playerState == UserState.DISCONNECTED)))
								{
									ISFSObject params = new SFSObject();
									params.putBool(ConstantClass.IS_HOT_TIME_EVENT, _isHotTime);
									params.putInt(ConstantClass.HOT_TIME_EVENT_TARGET, RewardTimes);
									params.putInt(ConstantClass.HOT_TIME_EVENT_CURRENT, _userHotTimePlay.get(i));
									params.putInt(ConstantClass.HOT_TIME_MISSION_MULTI, _hotTimeMissionMulti);
									this.send(ConstantClass.GAME_HOT_TIME_EVENT, params, sendUser);
									if (RewardTimes == _userHotTimePlay.get(i))
									{
										/* BET CLASS
											// Beginner : 500, 1K, 3K ,5K -> 0-3
											// Expert   : 10K, 20K, 50K, 100k, 250K -> 4-8
											// Master   : 1M, 5M, 10M, 25M -> 9-12
										*/
										_userHotTimePlay.put(i, 0);
										int availableClass = 0;
										if (_betGrade >= 4 && _betGrade <= 8)
										{
											availableClass = 1;
										}
										else if (_betGrade >= 9)
										{
											availableClass = 2;
										}
										dm.Insert_HotTime_RewardBox(playerID, availableClass);
									}
								}
							}
						}
					}
				}
			}
			else 
			{
				if (_isHotTime)
				{
					_isHotTime = false;
					_hotTimeTarget = 0;
					for (int i = 0; i < 4; i++)
					{
						_userHotTimePlay.put(i, 0);
						_userTaxBuff.put(i, 1f);
					}
				}
			}
		}
		catch(Exception ex)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("HotTime : %s", ErrorClass.StackTraceToString(ex)));
		}
	}

	private void MissionSetup() throws Exception
	{
		_currentMission = ConstantClass.MISSION_NONE;
		_hotTimeMissionMulti = 1;
		if (_hasMission)
		{
			int specialChance = 80;
			if (_isHotTime)
			{
				int randomMulti = _random.nextInt(1000);
				if (randomMulti < 180)
				{
					_hotTimeMissionMulti = 2;
				}
				else if (randomMulti < 360)
				{
					_hotTimeMissionMulti = 3;
				}
				else if (randomMulti < 530)
				{
					_hotTimeMissionMulti = 5;
				}
				else if (randomMulti < 680)
				{
					_hotTimeMissionMulti = 7;
				}
				else if (randomMulti < 780)
				{
					_hotTimeMissionMulti = 8;
				}
				else if (randomMulti < 850)
				{
					_hotTimeMissionMulti = 9;
				}
				else if (randomMulti < 900)
				{
					_hotTimeMissionMulti = 10;
				}
				else if (randomMulti < 940)
				{
					_hotTimeMissionMulti = 12;
				}
				else if (randomMulti < 970)
				{
					_hotTimeMissionMulti = 15;
				}
				else if (randomMulti < 990)
				{
					_hotTimeMissionMulti = 18;
				}
				else if (randomMulti < 1000)
				{
					_hotTimeMissionMulti = 20;
				}
				specialChance = 90;
			}
			int rs = _random.nextInt(100);			
			if(rs < specialChance)
			{
				//6,7,12,13,14,15,16,17,18
				//int[] as = {6,7,12,13,14,15,16,17,18}; old one
				int[] as = {5,6,7,8,11,12,13,16,17,18};		
				_currentMission = as[_random.nextInt(9)];
			}
			else
			{
				_currentMission = 1 + _random.nextInt(17);
			}
			int mid = get_currentMission();
			
			int missionmultiple = GetMissionMultiple(mid);
			
			ISFSObject params = new SFSObject();
			params.putInt(ConstantClass.MISSION_ID, mid);
			params.putInt(ConstantClass.MISSION_OBTAIN, missionmultiple);
			params.putBool(ConstantClass.IS_TOURNAMENT, false);
			this.send(ConstantClass.MISSION, params, GetUserList(-1));
		}
	}
	
	public int GetMissionMultiple(int mid)
	{
		int missionmultiple = 1;
		switch(mid)
		{
			case 1:
				missionmultiple = 6;
				break;
			case 2:
				missionmultiple = 6;
				break;
			case 3:
				missionmultiple = 6;
				break;
			case 4:
				missionmultiple = 6;
				break;
			case 5:
				missionmultiple = 3;
				break;
			case 6:
				missionmultiple = 3;
				break;
			case 7:
				missionmultiple = 6;
				break;
			case 8:
				missionmultiple = 3;
				break;
			case 9:
				missionmultiple = 4;
				break;
			case 10:
				missionmultiple = 4;
				break;
			case 11:
				missionmultiple = 6;
				break;
			case 12:
				missionmultiple = 3;
				break;
			case 13:
				missionmultiple = 6;
				break;
			case 14:
				missionmultiple = 2;
				break;
			case 15:
				missionmultiple = 2;
				break;
			case 16:
				missionmultiple = 3;
				break;
			case 17:
				missionmultiple = 3;
				break;
			case 18:
				missionmultiple = 6;
				break;
		}
		return missionmultiple;
	}
	
	private boolean NewCheckDragon()
	{
		boolean hasDragon = false;
		try
		{
			int idx = 0;
			int BestDragonSeat = -1;
			int DragonRank = 0;
			boolean GrandDragon = false;
			ArrayList<DragonWin> aldw = new ArrayList<DragonWin>();
			// Check Every Player if had Dragon
			for (int i = 0; i < 4; i++)
			{
				ArrayList<Card> cardslist = new ArrayList<Card>();			
				int pidx = _userSeat.get(i);
				if(pidx != -1 && (_userStatus.get(i) != UserState.WAIT_NEXT_GAME))
				{
					ArrayList<String> sendDeck = _userCard.get(i);
				    for (String sc : sendDeck)
				    {
						String str_suit = sc.substring(0, 1);
						String str_rank = sc.substring(1, sc.length());
						short suit = 0;
						short rank = (short)(Short.parseShort(str_rank) - 1);
						switch(str_suit)
						{
							case "D":
								suit = 1;
								cardslist.add(new Card(suit, rank));
								break;
							case "C":
								suit = 2;
								cardslist.add(new Card(suit, rank));
								break;
							case "H":
								suit = 3;
								cardslist.add(new Card(suit, rank));
								break;
							case "S":
								suit = 4;
								cardslist.add(new Card(suit, rank));
								break;
						}
				    }
				    //Check Dragon Win
				    DragonWin dw = new DragonWin(cardslist);
				    int ret = dw.retDragonCheck();
				    //if player has Dragon, add arraylistDragonWin(aldw)
				    if(ret != 0)
				    {
				    	_hasDragon = true;
				    	idx++;
					    //if two or more player has Dragon, Compare Dragon
					    if(idx > 1)
					    {
					    	aldw.add(dw);
					    	
					    	int retCompare = aldw.get(idx-1).compareTo(aldw.get(idx-2));
					    	
					    	if(retCompare == 1)
					    	{
						    	if(ret == 2)
						    	{
						    		GrandDragon = true;
						    	}
						    	DragonRank = ret;
					    		BestDragonSeat = i;
					    	}
					    }
					    else 
					    {
					    	if(ret == 2)
					    	{
					    		GrandDragon = true;
					    	}
					    	DragonRank = ret;
					    	BestDragonSeat = i;
					    	aldw.add(dw);
					    }
				    }
				}
			}
			if(idx > 0)
			{
				_dragonWinner = BestDragonSeat;
				_isGrandDragon = GrandDragon;
				_dragonRank = DragonRank;
				hasDragon = true;
			}
		}
		catch(Exception e)
		{
			hasDragon = false;
			LogMessage(ExtensionLogLevel.ERROR, String.format("CheckDragon : %s.", ErrorClass.StackTraceToString(e)));
		}
		return hasDragon;
	}
	
	public void NewGameResult() throws Exception
	{
		_isLeagueActive = (boolean)this.getParentZone().getExtension().handleInternalMessage("IsLeagueActive", null);
		
		// Check if Dragon
		if(_hasDragon)
		{
			ArrayList<String> TotalResult = new ArrayList<String>();
			long multiple = 100;
			if(_isGrandDragon)
			{
				multiple = 200;
			}
			long totalearn = 0;
			long basiccoin = _baseBet * multiple;
			// Calculating Lose Player
			for(int i = 0; i < 4; i++)
			{
				int pidx = _userSeat.get(i);
				long coin = GetUserCoin(i);
				long newCoin = 0;
				long logCoin = 0;
				long exp = 0;
				short win = 0;
				if(pidx != -1 && _userStatus.get(i) != UserState.WAIT_NEXT_GAME)
				{
					if(i != _dragonWinner)
					{
						win = -1;
						long temp_exp = getCurrentExp(i);
						int temp_lvl = GetLevel(temp_exp);
						exp = AddExp(win, i);
						int current_lvl = GetLevel(exp);
						short islvlup = 0;
						if(temp_lvl < current_lvl)
						{
							islvlup = 1;
						}
						//BankRoupt
						if(coin <= basiccoin)
						{
							totalearn += coin;
							newCoin = 0;
							logCoin = coin;
							TotalResult.add(String.format("%d:%d:%d:%d:%d:%d:0",pidx,coin*-1,2,newCoin,islvlup,current_lvl));
						}
						else 
						{	
							totalearn += basiccoin;
							newCoin = coin - (basiccoin);
							logCoin = basiccoin;
							TotalResult.add(String.format("%d:%d:%d:%d:%d:%d:0",pidx,basiccoin*-1,0,newCoin,islvlup,current_lvl));
						}
						addResultToDatabase(pidx, exp ,win, logCoin*-1, i);
					}
				}
				else 
				{
					TotalResult.add(String.format("%d:%d:%d:%d:%d:%d:0",-1,-1,0,0,0,0));		
				}
			}
			// Calculating Winner Dragon
			int winPlayer =  _userSeat.get(_dragonWinner);
			long usercoin = _userCoin.get(_dragonWinner);
			short win = 1;
			double f1 = totalearn;
			
			 double collected_tax = f1 * get_currentTax(_dragonWinner);
			 AddJackpot((float)collected_tax);
			 
			totalearn = Math.round(f1 - collected_tax);
			usercoin = usercoin + totalearn;
			long temp_exp = getCurrentExp(_dragonWinner);
			int temp_lvl = GetLevel(temp_exp);
			long exp = AddExp(win, _dragonWinner);
			int current_lvl = GetLevel(exp);
			short islvlup = 0;
			if(temp_lvl < current_lvl)
			{
				islvlup = 1;
			}
			
			long tempLeaguePoint = 0;
			if (!_userType.get(_dragonWinner).equals("Guest") && _isLeagueActive)
			{
				tempLeaguePoint = Math.round(totalearn / 1000.0d);
				if (tempLeaguePoint == 0) tempLeaguePoint = 1;
			}
			TotalResult.add(String.format("%d:%d:%d:%d:%d:%d:%d",winPlayer,totalearn,1,usercoin,islvlup,current_lvl,tempLeaguePoint));
			
			addResultToDatabase(winPlayer, exp, win, totalearn, _dragonWinner);

			Collections.reverse(TotalResult);
			//Make Sort Dragon Card
			Card[] sendcardAry = new Card[13];
			ArrayList<String> dragonDeck = _userCard.get(_dragonWinner);
			ArrayList<String> sendDeck = new ArrayList<String>();
			int aidx = 0;
		    for (String sc : dragonDeck)
		    {
				String str_suit = sc.substring(0, 1);
				String str_rank = sc.substring(1, sc.length());
				short suit = 0;
				short rank = (short) (Short.parseShort(str_rank) - 1);
				switch(str_suit)
				{
					case "D":
						suit = 1;
						break;
					case "C":
						suit = 2;
						break;
					case "H":
						suit = 3;
						break;
					case "S":
						suit = 4;
						break;
				}
				sendcardAry[aidx] = new Card(suit, rank);
				aidx++;
		    }
		    Card[] cclone = sendcardAry.clone();
	    	Arrays.sort(cclone, new CardComparator());
	    	for(int i = 0; i < cclone.length; i++)
	    	{
	    		sendDeck.add(cclone[i].toString());
	    	}
			
			//Send Dragon
			ISFSObject sendObj = new SFSObject();
			sendObj.putInt(ConstantClass.DRAGON_MODIFIER, _dragonRank);
			sendObj.putUtfStringArray(ConstantClass.DRAGON_CARDS, sendDeck);
			sendObj.putUtfStringArray(ConstantClass.TOTAL_RESULT, TotalResult);
			sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
			this.send(ConstantClass.DRAGON_RESULT, sendObj, GetUserList(-1));
			
			threadPool.execute(new UpdateSpecialWin(winPlayer, (byte) 1));
			// Log Dragon win
			String LogWinType = "DRAGON";
			if (_isGrandDragon)
			{
				LogWinType = "GRAND DRAGON";
			}
			threadPool.execute(new LogInstantWin(LogWinType, winPlayer, totalearn));
			
			lastTotalResult.clear();
			lastTotalResult.addAll(TotalResult);
			lastResultID = 1;
			lastWinCard.clear();
			lastWinCard.addAll(sendDeck);
			lastDragonModifier = _dragonRank;		
			
			// Jackpot Win
			if (_betGrade >= 9)
			{
				long savedJackpot = (long) this.getParentZone().getExtension().handleInternalMessage("GetJackpot", null);
				jackpotMinusAmount = (long) Math.floor(savedJackpot/2.0f);
				jackpotType = "DRAGON";
				if (_isGrandDragon) 
				{
					jackpotMinusAmount = savedJackpot;
					jackpotType = "GRAND DRAGON";
				}
				jackpotWinAmount = jackpotMinusAmount;
				if (_isHotTime) jackpotWinAmount = jackpotMinusAmount * 3;
				isWinJackpot = true;
				jackpotWinner =  _userSeat.get(_dragonWinner);
				jackpotWinnerNickname = _userNickname.get(_dragonWinner);
			}
		}
		else // Not Dragon Win
		{
			//Check Instant Win
			int index = 0;
			int BestIWSeat = -1;
			int BestIW = 0;
			ArrayList<InstantWin> aliw = new ArrayList<InstantWin>();
			ArrayList<String> IWResult = new ArrayList<String>();
			for(int idx = 0; idx < 4; idx++)
			{
				int pidx = _userSeat.get(idx);
				int seat = getUserSeat(pidx);
				try
				{
					// If not arrange no instant win
					if((pidx != 1) && ((_userStatus.get(idx) == UserState.DONE) || (_userStatus.get(idx) == UserState.DISCONNECTED_DONE)))
					{
						//Get User Make Card And Check Deck Valid
						ArrayList<Integer> usermakecard = _userMakeCard.get(pidx);
						boolean is_ValidDeck = checkValidDeck(seat,usermakecard);
						if(is_ValidDeck)
						{
							//Start Check Instant Win
							ArrayList<Card> getDeck = new ArrayList<Card>();
						    for (int x = 0; x < usermakecard.size(); x++)
						    {
						    	short suitnum = (short) ((usermakecard.get(x)+13)/13);
						    	short ranknum = (short) ((usermakecard.get(x)+13)%13);
						    	Card card = new Card(suitnum,ranknum);
						    	getDeck.add(card);
						    }
						    InstantWin returnIW = new InstantWin(getDeck);
						    //Return 0 : No Instant Win, Return 4 : FivePairs&Triple: (24x, 48x)
						    //Return 3 : Six Pairs(20x,40x),Return 2 : 3Straights(16x,32x),Return 1 : 3Flushses(12x,24x)
						    int ret = returnIW.retInstantWin();
						    if(ret != 0)
						    {
						    	_hasInstantWin = true;
						    	index++;
							    if(index > 1)
							    {
							    	aliw.add(returnIW);
							    	
							    	int retCompare = aliw.get(index-1).compareTo(aliw.get(index-2));
							    	
							    	if(retCompare == 1)
							    	{
							    		BestIWSeat = idx;
							    		BestIW = ret;
							    	}
							    }
							    else 
							    {
							    	BestIWSeat = idx;
							    	BestIW = ret;
							    	aliw.add(returnIW);
							    }
						    }
						}
					}
					else
					{
						if (pidx > 0)
						{
							LogMessage(String.format("GameResult InstantWin : Card %d it's not valid card", pidx));
						}
					}
				}
				catch(Exception e)
				{
					LogMessage(ExtensionLogLevel.ERROR, String.format("GameResult InstantWin : %s", ErrorClass.StackTraceToString(e)));
				}
			}
			// Have InstantWin
			if(index > 0)
			{
				long multiple = 1;
				switch(BestIW) 
				{
					case 4:
						multiple = 24;
						break;
					case 3:
						multiple = 20;
						break;
					case 2:
						multiple = 16;
						break;
					case 1:
						multiple = 12;
						break;
				}
				if(_totalPlayer == 4)
				{
					multiple = multiple * 2;
				}
				long totalearn = 0;
				long basiccoin = _baseBet * multiple;
				for(int i = 0; i < 4; i++)
				{
					int pidx = _userSeat.get(i);
					long coin = _userCoin.get(i);
					long newCoin = 0;
					long LogCoin = 0;
					long exp = 0;
					short win = 0;
					if(pidx != -1 && _userStatus.get(i) != UserState.WAIT_NEXT_GAME)
					{
						if(i != BestIWSeat)
						{	
							win = -1;
							int seat = getUserSeat(pidx);
							long temp_exp = getCurrentExp(seat);
							int temp_lvl = GetLevel(temp_exp);
							exp = AddExp(win, seat);
							int current_lvl = GetLevel(exp);
							short islvlup = 0;
							if(temp_lvl < current_lvl)
							{
								islvlup = 1;
							}
							//Bankrupt
							if(coin <= basiccoin)
							{
								totalearn += coin;
								newCoin = 0;
								LogCoin = coin;
								IWResult.add(String.format("%d:%d:%d:%d:%d:%d:%d:0",pidx,coin*-1,2,BestIW,newCoin,islvlup,current_lvl));
							}
							else 
							{	
								totalearn += basiccoin;
								newCoin = coin - (basiccoin);
								LogCoin = basiccoin;
								IWResult.add(String.format("%d:%d:%d:%d:%d:%d:%d:0",pidx,basiccoin*-1,0,BestIW,newCoin,islvlup,current_lvl));
							}
							addResultToDatabase(pidx, exp, win, LogCoin*-1, seat);
						}
					}
					else 
					{
						IWResult.add(String.format("%d:%d:%d:%d:%d:%d:%d:0",-1,-1,0,0,0,0,0));	
					}
				}
				// Win InstantWin Player
				int winPlayer =  _userSeat.get(BestIWSeat);
				long usercoin = _userCoin.get(BestIWSeat);
				short win = 1;
				double f1 = totalearn;
				double collected_tax = f1 * get_currentTax(BestIWSeat);
				AddJackpot((float)collected_tax);
				totalearn = Math.round(f1 - collected_tax);
				usercoin = usercoin + totalearn;
				long temp_exp = getCurrentExp(BestIWSeat);
				int temp_lvl = GetLevel(temp_exp);
				long exp = AddExp(win,BestIWSeat);
				int current_lvl = GetLevel(exp);
				short islvlup = 0;
				if(temp_lvl < current_lvl)
				{
					islvlup = 1;
				}
				
				long tempLeaguePoint = 0;
				if (!_userType.get(BestIWSeat).equals("Guest") && _isLeagueActive)
				{
					tempLeaguePoint = Math.round(totalearn / 1000.0d);
					if (tempLeaguePoint == 0) tempLeaguePoint = 1;
				}
				IWResult.add(String.format("%d:%d:%d:%d:%d:%d:%d:%d",winPlayer,totalearn,1,BestIW,usercoin,islvlup,current_lvl,tempLeaguePoint));
				
				addResultToDatabase(winPlayer, exp, win, totalearn, BestIWSeat);
				
				/* instatnt win log */
				String LogWinType = "3 FLUSHES";
				if (BestIW == 2)
				{
					LogWinType = "3 STRAIGHTS";
				}
				else if (BestIW == 3)
				{
					LogWinType = "6 PAIRS";
				}
				else if (BestIW == 4)
				{
					LogWinType = "5 PAIRS + TRIPLE";
				}
				threadPool.execute(new LogInstantWin(LogWinType, winPlayer, totalearn));
								
				Collections.reverse(IWResult);
				ArrayList<Integer> getDeck = null;
				ArrayList<String> sendDeck =null;
				try
				{
					getDeck = _userMakeCard.get(winPlayer);
					sendDeck = new ArrayList<String>();
				    for (int x = 0; x < getDeck.size(); x++)
				    {
				    	short suitnum = (short) ((getDeck.get(x)+13)/13);
				    	short ranknum = (short) ((getDeck.get(x)+13)%13);
				    	
				    	Card card = new Card(suitnum,ranknum);
				    	
				    	sendDeck.add(card.toString());
				    }
				}
				catch(Exception ex)
				{
					getDeck = new ArrayList<Integer>();
					sendDeck = new ArrayList<String>();
					LogMessage(ExtensionLogLevel.ERROR, String.format("GameResult InstantWin : %s", ErrorClass.StackTraceToString(ex)));
				}
				
				
				ISFSObject sendObj = new SFSObject();
				sendObj.putUtfStringArray(ConstantClass.INSTANTWIN_CARDS, sendDeck);
				sendObj.putUtfStringArray(ConstantClass.TOTAL_RESULT, IWResult);
				sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
				this.send(ConstantClass.INSTANTWIN_RESULT, sendObj, GetUserList(-1));
				
				threadPool.execute(new UpdateSpecialWin(winPlayer, (byte) 2));
				
				lastTotalResult.clear();
				lastTotalResult.addAll(IWResult);
				lastResultID = 2;
				lastWinCard.clear();
				lastWinCard.addAll(sendDeck);

				// Jackpot Win
				if ((BestIW == 4) && (_betGrade >= 9))
				{
					long savedJackpot = (long) this.getParentZone().getExtension().handleInternalMessage("GetJackpot", null);
					jackpotMinusAmount = (long) Math.floor(savedJackpot * 0.25f);
					jackpotWinAmount = jackpotMinusAmount;
					if (_isHotTime) jackpotWinAmount = jackpotMinusAmount * 3;
					isWinJackpot = true;
					jackpotWinner =  _userSeat.get(BestIWSeat);
					jackpotWinnerNickname = _userNickname.get(BestIWSeat);
					jackpotType = "5 PAIRS + TRIPLE";
				}
			} //============ Instant Win End ===============
			else // No Instant Win... Normal Check Result
			{
				int UserSeatNum = 0;
				Hand[] AllPlayerTopHand = new Hand[4];
				Hand[] AllPlayerMiddleHand = new Hand[4];
				Hand[] AllPlayerBackHand = new Hand[4];
				ArrayList<String> p1result = new ArrayList<String>();
				ArrayList<String> p2result = new ArrayList<String>();
				ArrayList<String> p3result = new ArrayList<String>();
				ArrayList<String> p4result = new ArrayList<String>();
				ArrayList<Integer> timeoutList = new ArrayList<Integer>();
				
				//Checking Salah Susun
				for(int idx = 0; idx < 4; idx++)
				{
					int pidx = _userSeat.get(idx);
					UserSeatNum = getUserSeat(pidx);
					if(pidx != -1 && UserSeatNum != -1 && (_userStatus.get(idx) != UserState.WAIT_NEXT_GAME))
					{
						ArrayList<Card> Top 	=  new ArrayList<Card>();  
						ArrayList<Card> Middle 	=  new ArrayList<Card>(); 
						ArrayList<Card> Back 	=  new ArrayList<Card>();
						if((_userStatus.get(idx) != UserState.DONE) && (_userStatus.get(idx) != UserState.DISCONNECTED_DONE))
						{
							timeoutList.add(pidx);
							
							short suit = 0, rank = 0;
							Back.add(new Card(suit,rank));
							Back.add(new Card(suit,rank));
							Back.add(new Card(suit,rank));
							Back.add(new Card(suit,rank));
							Back.add(new Card(suit,rank));
							
							Middle.add(new Card(suit,rank));
							Middle.add(new Card(suit,rank));
							Middle.add(new Card(suit,rank));
							Middle.add(new Card(suit,rank));
							Middle.add(new Card(suit,rank));
							
							Top.add(new Card(suit,rank));
							Top.add(new Card(suit,rank));
							Top.add(new Card(suit,rank));
							
							AllPlayerTopHand[UserSeatNum]		= new Hand(Top);
						    AllPlayerMiddleHand[UserSeatNum] 	= new Hand(Middle);
						    AllPlayerBackHand[UserSeatNum] 		= new Hand(Back);
						    
						    trace(String.format("SALAHSUSUN:TIMEOUT:%s", pidx));
						}
						else //UserStatus is Done PLayer
						{	
							ArrayList<Integer> usermakecard = _userMakeCard.get(pidx);
							boolean is_ValidDeck = checkValidDeck(UserSeatNum,usermakecard);
						    if(!is_ValidDeck)
						    {
						    	//Error Handle(Bad Arrange) Send User Client Not Valid Card
								short suit = 0, rank = 0;
								Back.add(new Card(suit,rank));
								Back.add(new Card(suit,rank));
								Back.add(new Card(suit,rank));
								Back.add(new Card(suit,rank));
								Back.add(new Card(suit,rank));
								
								Middle.add(new Card(suit,rank));
								Middle.add(new Card(suit,rank));
								Middle.add(new Card(suit,rank));
								Middle.add(new Card(suit,rank));
								Middle.add(new Card(suit,rank));
								
								Top.add(new Card(suit,rank));
								Top.add(new Card(suit,rank));
								Top.add(new Card(suit,rank));
								
								AllPlayerTopHand[UserSeatNum]		= new Hand(Top);
							    AllPlayerMiddleHand[UserSeatNum] 	= new Hand(Middle);
							    AllPlayerBackHand[UserSeatNum] 		= new Hand(Back);
							    
							    trace(String.format("SALAHSUSUN:INVALID:%s", pidx));
						    }
						    else 
						    {
							    //Make Player Hand(5.5.3 CUT)
								//make BackHand
								for(int idx1 = 0; idx1 < 5; idx1++)
								{
									short suit = (short) ((usermakecard.get(idx1)+13) / 13);
									short rank = (short) ((usermakecard.get(idx1)+13) % 13);
									Card card = new Card(suit,rank);
									Back.add(card);
								}
								//make Middle
								for(int idx1 = 5; idx1 < 10; idx1++)
								{
									short suit = (short) ((usermakecard.get(idx1)+13) / 13);
									short rank = (short) ((usermakecard.get(idx1)+13) % 13);
									Card card = new Card(suit,rank);
									Middle.add(card);
								}
								//make Front
								for(int idx1 = 10; idx1 < 13; idx1++)
								{
									short suit = (short) ((usermakecard.get(idx1)+13) / 13);
									short rank = (short) ((usermakecard.get(idx1)+13) % 13);
									Card card = new Card(suit,rank);
									Top.add(card);
								}
								Hand FrontHand = new Hand(Top);
								Hand MiddleHand = new Hand(Middle);
								Hand BackHand = new Hand(Back);
								//Check Bad Arrange
								if(BackHand.compareTo(MiddleHand) == 1 && MiddleHand.compareTo(FrontHand) == 1)
								{
									AllPlayerTopHand[UserSeatNum]		= FrontHand;
								    AllPlayerMiddleHand[UserSeatNum] 	= MiddleHand;
								    AllPlayerBackHand[UserSeatNum] 		= BackHand;
								}
								else
								{
									trace(String.format("SALAHSUSUN:NORMAL:%s:%s:%s:%s", pidx, FrontHand.displayAll(), MiddleHand.displayAll(), BackHand.displayAll()));
									
									//error Handle Bad Arrange									
									short suit = 0, rank = 0;
									Back.add(new Card(suit,rank));
									Back.add(new Card(suit,rank));
									Back.add(new Card(suit,rank));
									Back.add(new Card(suit,rank));
									Back.add(new Card(suit,rank));
									
									Middle.add(new Card(suit,rank));
									Middle.add(new Card(suit,rank));
									Middle.add(new Card(suit,rank));
									Middle.add(new Card(suit,rank));
									Middle.add(new Card(suit,rank));
									
									Top.add(new Card(suit,rank));
									Top.add(new Card(suit,rank));
									Top.add(new Card(suit,rank));
									
									AllPlayerTopHand[UserSeatNum]		= new Hand(Top);
								    AllPlayerMiddleHand[UserSeatNum] 	= new Hand(Middle);
								    AllPlayerBackHand[UserSeatNum] 		= new Hand(Back);
								}
							}
						}
					}
				}
				
				int _totalPlayerForCalc = _totalPlayer;
		        //Init Variables
				RoundResult[] AllRoundResult = new RoundResult[3];
				for(int idx = 0; idx < 3; idx++)
				{
					switch(idx)
					{
						case 0:
							AllRoundResult[idx] = new RoundResult(this, AllPlayerTopHand,1);
							break;
						case 1:
							AllRoundResult[idx] = new RoundResult(this, AllPlayerMiddleHand,2);
							break;
						case 2:
							AllRoundResult[idx] = new RoundResult(this, AllPlayerBackHand,3);
							break;
					}
				}
							
				Result rs = new Result(this, AllRoundResult, _totalPlayerForCalc);
				
				if (_totalPlayer != _totalPlayerForCalc)
				{
					LogMessage(ExtensionLogLevel.ERROR, String.format("Magic Error, totalPlayer : %d, temp : %d", _totalPlayer, _totalPlayerForCalc));
				}
				
				ArrayList<ArrayList<String>> alrr = rs.retRoundResult();
				for(int i = 0; i < alrr.size(); i++)
				{
					ArrayList<String> frr = alrr.get(i);
					for(String s : frr)
					{
						String[] k = s.split(":");
						int pidx = Integer.parseInt(k[1]);
						int seat = getUserSeat(pidx);
						if(pidx >= 0)
						{
							switch(seat)
							{
								case 0:
									p1result.add(s);
									break;
								case 1:
									p2result.add(s);
									break;
								case 2:
									p3result.add(s);
									break;
								case 3:
									p4result.add(s);
									break;
							}
						}
					}
				}

				//Result Send User
				for(int x = 0; x < 4; x++)
				{
					int pidx = _userSeat.get(x);
					User user = this.getParentRoom().getUserByName(Integer.toString(pidx));
					if(user != null)
					{
						if(pidx >= 0)
						{
							ISFSObject params = new SFSObject();
							params.putUtfStringArray(ConstantClass.TOTAL_RESULT, rs.retTotalResult());
							params.putBool(ConstantClass.IS_TOURNAMENT, false);
							if (timeoutList.contains(pidx))
							{
								params.putBool(ConstantClass.TIMEOUT_SALAHSUSUN, true);
							}
							else
							{
								params.putBool(ConstantClass.TIMEOUT_SALAHSUSUN, false);
							}
							switch(x)
							{
								case 0:
								{
									params.putUtfStringArray(ConstantClass.ROUND_RESULT, p1result);
									this.send(ConstantClass.GAME_RESULT, params, user);
								}
								break;
								case 1:
								{
									params.putUtfStringArray(ConstantClass.ROUND_RESULT, p2result);
									this.send(ConstantClass.GAME_RESULT, params, user);
								}
								break;
								case 2:
								{
									params.putUtfStringArray(ConstantClass.ROUND_RESULT, p3result);

									this.send(ConstantClass.GAME_RESULT, params, user);
								}
								break;
								case 3:
								{
									params.putUtfStringArray(ConstantClass.ROUND_RESULT, p4result);
									this.send(ConstantClass.GAME_RESULT, params, user);
								}
								break;
							}
						}
					}
				}
				lastTotalResult.clear();
				lastTotalResult.addAll(rs.retTotalResult());
				lastResultID = 0;
			}
		}
		try
		{
			// Alert Master, String UserID:Coin:Exp
			ArrayList<String> stringCoin = new ArrayList<String>(); 
			for(int i = 0; i < 4; i++)
			{
				stringCoin.add(String.format("%d:%d:%d", _userSeat.get(i), _userCoin.get(i), _userExp.get(i)));
			}
			ISFSObject objInform = new SFSObject();
			objInform.putUtfString("StringCoin1", stringCoin.get(0));
			objInform.putUtfString("StringCoin2", stringCoin.get(1));
			objInform.putUtfString("StringCoin3", stringCoin.get(2));	
			objInform.putUtfString("StringCoin4", stringCoin.get(3));	
			SendInterlaMessage(ConstantClass.GAME_SERVER_UPDATE_GAME_RESULT, objInform);
		}
		catch(Exception e)
		{
			LogMessage(ExtensionLogLevel.ERROR, String.format("GameFinish : %s.", ErrorClass.StackTraceToString(e)));
		}
	}
	
	boolean checkValidDeck(int idx, ArrayList<Integer> usermakecard)
	{
		boolean s = true;
		try
		{
			ArrayList<String> getDeck = new ArrayList<String>();
			ArrayList<String> str_userDeck = _userCard.get(idx);
			
		    for (int x = 0; x < usermakecard.size(); x++)
		    {
		    	short suitnum = (short) ((usermakecard.get(x)+13)/13);
		    	short ranknum = (short) ((usermakecard.get(x)+13)%13);	
		    	Card card = new Card(suitnum,ranknum);
		    	getDeck.add(card.toString());
		    }
		    
		    //Start Check Card(Check Deck Original and UserMakeCard)
		    Set<String> set1 = new HashSet<String>(str_userDeck);
		    for (String foo : getDeck)
		    {
		        if (!set1.contains(foo)) 
		        {
		        	s = false;
		        }
		    }
		    /*
		    if(!s)
		    {
			    String aa = "";
			    String bb = "";
				for(String gds : getDeck)
				{
					aa += gds;
				}
				for(String uds : str_userDeck)
				{
					bb += uds;
				}
				LogMessage(String.format("checkValidDeck : %d Card Check Error Original Card : %s, Get Card : %s", idx, bb, aa));
		    }
		    */
		}
		catch(Exception ex)
		{
			s = false;
			LogMessage(ExtensionLogLevel.ERROR, String.format("checkValidDeck : %s", ErrorClass.StackTraceToString(ex)));
		}
    	return s;
	}


	/*
	 * Room Scheduler
	 */
	
	private int _secondsCounter = 1000;
	private int testerCounter = 0;
	private class GameLoop implements Runnable
	{
		public void run()
		{
			try
			{
				switch (_loopIndex)
				{
					case 0: // Room Init, wait for Room Config
					{
						_loopCounter -= _loopStepInterval;
						if (_loopCounter <= 0)
						{
							LogMessage(ExtensionLogLevel.ERROR, "No Room Setup.");
							_isRoomDestroyed = true;	
							SendInterlaMessage("DeleteRoomNoConfig", new SFSObject());
							_loopIndex = 999;
						}
					}
					break;
					case 5: // Room Configured, Wait for Player enter
					{
						_loopCounter -= _loopStepInterval;
						if (_loopCounter <= 0)
						{
							LogMessage(ExtensionLogLevel.ERROR, "Creator or Player not enter in 30 seconds.");
							_isRoomDestroyed = true;
							SendInterlaMessage("DeleteRoom", new SFSObject());
							_loopIndex = 999;
						}
					}
					break;
					case 100: // WAIT_START_GAME, Wait for more users to start the game.
					{
						// Check if user not exist
						for(int i  = 0; i < 4; i++)
						{
							int playerID = _userSeat.get(i);
							if ( playerID >= 0)
							{
								if ( InRoomExtension.this.getParentRoom().getUserByName(Integer.toString(playerID)) == null)
								{
									HandleUserLeave(playerID, i, "User not available");
								}
							}
						}
						// If 2 or more player, start counting down
						int nPlayers = GetPlayerCount();
						if ( nPlayers >= 2)
						{
							_currentState = RoomState.COUNTDOWN_START_GAME;
							_loopCountDown = 7;
							_loopCounter = 2000;
							_loopIndex = 105;
						}
						else if (nPlayers == 0)
						{
							LogMessage("Room Empty, Delete Room now.");
							_isRoomDestroyed = true;
							SendInterlaMessage("DeleteRoom", new SFSObject());
							_loopIndex = 999;
						}
					}
					break;
					case 105: // COUNTDOWN_START_GAME, Wait 2 Seconds before Start counting down
					{
						if (GetPlayerCount() < 2)
						{
							LogMessage("Canceling Count down, not enough Player to start Game.");
							
							_currentState = RoomState.WAIT_START_GAME;
							InRoomExtension.this.send(ConstantClass.WAIT_FOR_OTHER_USER, new SFSObject(), GetUserList(-1));
							_loopIndex = 100;
						}
						else
						{
							_loopCounter -= _loopStepInterval;
							if (_loopCounter <= 0)
							{
								_loopCountDown = 7;
								_loopIndex = 110;
							}
						}
					}
					break;
					case 110: // COUNTDOWN_START_GAME, alert user every second count down
					{
						if (GetPlayerCount() < 2)
						{
							LogMessage("Canceling Count down, not enough Player to start Game.");
							
							_currentState = RoomState.WAIT_START_GAME;
							InRoomExtension.this.send(ConstantClass.WAIT_FOR_OTHER_USER, new SFSObject(), GetUserList(-1));
							_loopIndex = 100;
						}
						else
						{
							_loopCounter -= _loopStepInterval;
							if(_loopCounter <= 0)
							{
								_loopCounter = 1000;
								ISFSObject sendObj = new SFSObject();
								sendObj.putInt(ConstantClass.TIME, _loopCountDown);
								sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
								InRoomExtension.this.send(ConstantClass.COUNTDOWN_START_GAME, sendObj, GetUserList(-1));
								if(_loopCountDown <= 0)
								{
									// Done Counting down, start game
									_isPlaying = true;
									_currentState = RoomState.SHUFFLE_CARD;
									_loopIndex = 115;
								}
								_loopCountDown--;
							}
						}
					}
					break;
					case 115: // SHUFFLE_CARD, Shuffle card, mission and HotTime
					{
						if (GetPlayerCount() < 2)
						{
							LogMessage("Canceling Count down, not enough Player to start Game.");
							_isPlaying = false;
							_currentState = RoomState.WAIT_START_GAME;
							InRoomExtension.this.send(ConstantClass.WAIT_FOR_OTHER_USER, new SFSObject(), GetUserList(-1));
							_loopIndex = 100;
						}
						else
						{
							ShuffleCard();
							_dragonWinner = -1;
							_isGrandDragon = false;
							_dragonRank = 0;
							
							ISFSObject PassParam = new SFSObject();
					    	PassParam.putBool(ConstantClass.IS_ROOM_PLAYING, true);
					    	SendInterlaMessage(ConstantClass.GAME_SERVER_UPDATE_ROOM_STATUS, PassParam);
					    	
					    	_totalPlayer = GetTotalPlayer();
							_loopIndex = 120;
						}
					}
					break;
					case 120: // SHUFFLE_CARD, Check Dragon
					{
						if (NewCheckDragon())
						{
							_currentState = RoomState.RESULT;
							_loopIndex = 750;
						}
						else
						{
							_loopCountDown = 4;
							_loopCounter = 1000;
							
							_loopIndex = 125;
						}
					}
					break;
					case 125: // SHUFFLE_CARD, wait 4 seconds before count down play game
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							_loopCountDown--;
							if(_loopCountDown <= 0)
							{
								_currentState = RoomState.GAME_PLAY;
								if(_isFastRoom)
								{
									_loopCountDown = FAST_PLAY_TIME;
								}
								else 
								{
									_loopCountDown = 60; // Normal play time
								}
								
								// Send User Start Cound Down
								ISFSObject sendObj = new SFSObject();
								sendObj.putInt(ConstantClass.TIME, _loopCountDown);
								sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
								sendObj.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
								InRoomExtension.this.send(ConstantClass.COUNTDOWN_PLAY_GAME, sendObj, GetUserList(-1));
								
								_loopIndex = 130;
							}
							else
							{
								_loopCounter = 1000;
							}
						}
					}
					break;
					case 130: // GAME_PLAY , alert Player Every Seconds
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							_loopCounter = 1000;
		
							// Send User Start Cound Down
							ISFSObject sendObj = new SFSObject();
							sendObj.putInt(ConstantClass.TIME, _loopCountDown);
							sendObj.putBool(ConstantClass.IS_TOURNAMENT, false);
							sendObj.putLong(ConstantClass.SERVER_TIME, System.currentTimeMillis());
							InRoomExtension.this.send(ConstantClass.COUNTDOWN_PLAY_GAME_WEB, sendObj, GetUserList(-1));
							
							if(_loopCountDown <= 0)
							{
								_loopIndex = 135;
							}
							_loopCountDown--;
						}
					}
					break;
					case 135: // GAME_PLAY, Setup Wait time
					{
						_loopCounter = 5000;
						_loopIndex = 140;
					}
					break;
					case 140: // GAME_PLAY, Wait 5 Sec
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							_loopIndex = 600;
						}
					}
					break;
					case 600: // RESULT, setup 1 Sec before enter Wait Result
					{
						_currentState = RoomState.RESULT;
						_loopCounter = 1000;
						_loopIndex = 605;
					}
					break;
					case 605: // RESULT, Wait 1 Sec, then alert player not done and go to result
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							if (_readycount != _totalPlayer)
							{
								for(int i = 0; i < 4; i++)
								{
									int pidx = _userSeat.get(i);
									if(pidx >= 0)
									{
										if((_userStatus.get(i) == UserState.PLAYING ) || (_userStatus.get(i) == UserState.DISCONNECTED))
										{
											User user = InRoomExtension.this.getParentRoom().getUserByName(Integer.toString(pidx));
											if (user != null)
											{
												InRoomExtension.this.send(ConstantClass.LATE_RESULT_WARNING, new SFSObject(), user);
											}
											else
											{
												LogMessage(ExtensionLogLevel.WARN, String.format("PlayerID %d disconected for late result warning.", pidx));
											}
										}
									}
								}
							}
							
							_StartResultTime = System.currentTimeMillis();
							_loopIndex = 700;
						}
					}
					break;
					case 700: // RESULT , calculate result
					{
						NewGameResult();
						_currentState = RoomState.IDLE;
						_loopIndex = 800;
					}
					break;
					case 750: // RESULT, Dragon Win
					{
						NewGameResult();
						_currentState = RoomState.IDLE;
						_loopIndex = 800;
					}
					break;
					case 800: // IDLE, animating Result
					{
						_isPlaying = false;
						_waitResult = true;
						for(int x = 0; x < 4; x++)
						{
							if (_userStatus.get(x) == UserState.WAIT_NEXT_GAME)
							{
								_userWaitResult.put(x, true);
							}
						}
						_loopCounter = 18000;
						if (_hasInstantWin)
						{
							_loopCounter = 5000;
						}
						else if (_hasDragon)
						{
							_loopCounter = 10000;
						}
				    	_readycount = 0;
						
						
						
						// Alert Master the Game become idle
				    	ISFSObject PassParam = new SFSObject();
				    	PassParam.putBool(ConstantClass.IS_ROOM_PLAYING, false);
				    	SendInterlaMessage(ConstantClass.GAME_SERVER_UPDATE_ROOM_STATUS, PassParam);

				    	_loopIndex = 801;
				    	
					}
					break;
					case 801:
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							if (isWinJackpot)
							{
								ISFSObject sendObj = new SFSObject();
								sendObj.putUtfString(ConstantClass.NICKNAME, jackpotWinnerNickname);
								sendObj.putInt(ConstantClass.USER_ID, jackpotWinner);
								sendObj.putLong(ConstantClass.JACKPOT_TOTAL, jackpotWinAmount);
								InRoomExtension.this.send(ConstantClass.WIN_JACKPOT, sendObj, GetUserList(-1));
								
								ISFSObject PassParam = new SFSObject();
								PassParam.putLong(ConstantClass.JACKPOT_TOTAL, jackpotWinAmount);
								PassParam.putLong(ConstantClass.JACKPOT_MINUS, jackpotMinusAmount);
								PassParam.putUtfString(ConstantClass.JACKPOT_TYPE, jackpotType);
								PassParam.putUtfString(ConstantClass.NICKNAME, jackpotWinnerNickname);
								PassParam.putInt(ConstantClass.USER_ID, jackpotWinner);
						    	SendInterlaMessage(ConstantClass.WIN_JACKPOT, PassParam);
						    	
								_loopCounter = 3000;
								_loopIndex = 805;
							}
							else
							{
								_loopCounter = 0;
								_loopIndex = 805;
							}
						}
					}
					break;
					case 805: // IDLE, wait animating time done
					{
						_loopCounter -= _loopStepInterval;
						if(_loopCounter <= 0)
						{
							ClearForNextMatch();
							
							_waitResult = false;
							
							_hasInstantWin = false;
							_hasDragon = false;
							//Kick User Disconnected
							for(int x = 0; x < 4; x++)
							{
								int pidx = _userSeat.get(x);
								int seat = getUserSeat(pidx);
								if((_userStatus.get(x) == UserState.DISCONNECTED) || (_userStatus.get(x) == UserState.DISCONNECTED_DONE))
								{
									HandleUserLeave(pidx, seat, "Disconnected");
								}
							}
							//Bankroupt User Kick
							BankrouptUserKick();
			   				for (int i = 0; i < 4; i++)
			   				{
			   					int playerID = _userSeat.get(i);
			   					if (playerID >= 0)
			   					{
			   						//Change UserStatus Playing
			   						_userStatus.put(i, UserState.PLAYING);
			   					}
			   				}
			   				
			   				_currentState = RoomState.COUNTDOWN_START_GAME;
							_loopCountDown = 7;
							_loopCounter = 2000;
							_loopIndex = 105;
						}
					}
					break;
						
				}
				
				
				// Room Check up every Seconds
				_secondsCounter -= 100;
				if (_secondsCounter <= 0)
				{
					if(_loopIndex >= 100)
					{
						int nUsers = InRoomExtension.this.getParentRoom().getSize().getTotalUsers();
						// TEST : Send Number to Client every Seconds 
						if (nUsers > 0)
						{
							ISFSObject sendObj = new SFSObject();
					        sendObj.putInt("NO", testerCounter);
					        InRoomExtension.this.send("TestTick", sendObj, GetUserList(-1));
							testerCounter++;
							if(testerCounter > 9) testerCounter -= 9;
						}
					}
					_secondsCounter = 1000;
				}
			}
			catch(Exception e)
			{
				ForceAllUserOut();
				_isRoomDestroyed = true;	
				SendInterlaMessage("DeleteRoom", new SFSObject());
				LogMessage(ExtensionLogLevel.ERROR, String.format("GameLoop Index : %d, Error : %s.", _loopIndex, ErrorClass.StackTraceToString(e)));
				_loopIndex = 999;
			}
		}
	}
	
	private class AddJackpotFromTax implements Runnable
	{
		final float incomingTax;
		public AddJackpotFromTax(float tax)
		{
			incomingTax = tax;
		}
		
		public void run()
		{
			float addon = incomingTax * 0.05f;
			InRoomExtension.this.getParentZone().getExtension().handleInternalMessage("AddJackpot", (long)addon);
		}
	}
	
	
	private class UpdateSpecialWin implements Runnable
	{
		final int playerID;
		final byte winType;
		
		public UpdateSpecialWin(int pidx, byte type)
		{
			playerID = pidx;
			winType = type;
		}
		
		public void run()
		{
			try
			{

				Integer result = DBManager.getInstance().UpdateSpecialWin(playerID, winType);
				if(result != 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, "UpdateSpecialWin : UpdateSpecialWin Sp Error.");
				}	
			}
			catch (Exception e)
			{
					LogMessage(ExtensionLogLevel.ERROR, String.format("UpdateSpecialWin : %s", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class LogInstantWin implements Runnable
	{
		final String winType;
		final int winPlayer;
		final long winCoin;
		
		public LogInstantWin(String logType, int logWinner, long totalEarn)
		{
			winType = logType;
			winPlayer = logWinner;
			winCoin = totalEarn;
		}
		
		public void run()
		{
			try
			{
				Integer logInstant = DBManager.getInstance().InsertLogInstantWin(_roomID, winType, winCoin, winPlayer, _userSeat.get(0), _userSeat.get(1), _userSeat.get(2), _userSeat.get(3));
				if(logInstant != 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, "CheckDragon Win : InsertLogInstantWin Sp Error.");
				}	
			}
			catch (Exception e)
			{
					LogMessage(ExtensionLogLevel.ERROR, String.format("LogInstantWin : %s", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class UpdatePlayerToDatabase implements Runnable
	{
		final int playerID;
		final long playerExp;
		final short winStatus;
		final long addCoin;
		
		public UpdatePlayerToDatabase(int pidx,	long exp, short win, long resultCoin)
		{
			playerID = pidx;
			playerExp = exp;
			winStatus = win;
			addCoin = resultCoin;
		}
		
		public void run()
		{
			try
			{
				int seat = getUserSeat(playerID);
				if (seat < 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, String.format("UpdatePlayerToDatabase : Invalid Seat %d index for PlayerID %d.", seat, playerID));
					return;
				}
				
				int level = GetLevel(playerExp);
				int levelReward = 0;
				if(_userOldLevel.get(seat) != level)
				{
					levelReward = level;
				}
	
				Object[] ret = DBManager.getInstance().updateUserGameResult(playerID, addCoin, _betGrade, playerExp, _userOldLevel.get(seat), levelReward, winStatus);
				int flag 	= (int)ret[0];
				if (flag != 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, "UpdatePlayerToDatabase : updateUserGameResult SP Error.");
					return;
				}
				long newCoin = (long)ret[1];
				long newExp 	= (long)ret[2];
				int newPlay = (int)ret[3];
				int newWin 	= (int)ret[4];
				int newLevel 	= GetLevel(newExp);
				putUserCoin(seat, newCoin);
				
				if(_userOldWin.get(seat) != newWin)
				{
					_userOldWin.put(seat, newWin);
					threadPool.execute(new CheckUserAchievement(playerID, 2, newWin));
				}
				if(_userOldPlay.get(seat) != newPlay)
				{
					_userOldPlay.put(seat, newPlay);
					threadPool.execute(new CheckUserAchievement(playerID, 0, newPlay));
				}
				if(_userOldLevel.get(seat) != newLevel)
				{
					_userOldLevel.put(seat, newLevel);
					threadPool.execute(new CheckUserAchievement(playerID, 1, newLevel));
				}
				
				HashMap<String,Object> leaguePoint = LeagueManager.getInstance().saveLeaguePoint((long) playerID, addCoin, DBManager.getInstance());
				if (leaguePoint != null)
				{
					int newGrade = (int)leaguePoint.get("grade");
					int newDivision = (int)leaguePoint.get("division");
					if ((_userLeagueGrade.get(seat) != newGrade) || (_userLeagueDivision.get(seat) != newDivision))
					{
						_userLeagueGrade.put(seat, newGrade);
						_userLeagueDivision.put(seat, newDivision);
						 // Alert Master Server
				        ISFSObject parseObj = new SFSObject();
				        parseObj.putInt(ConstantClass.USER_ID, playerID);
				        parseObj.putInt(ConstantClass.LEAGUE_GRADE, newGrade);
				        parseObj.putInt(ConstantClass.LEAGUE_DIVISION, newDivision);
				        parseObj.putInt(ConstantClass.SEAT_INDEX, seat + 1);
				        SendInterlaMessage(ConstantClass.USER_UPDATE_LEAGUE, parseObj);
					}
				}

				Integer logMission = DBManager.getInstance().UpdateDailyMission(playerID, (byte)(_isFastRoom?1:0), (byte)(_hasMission?1:0), (byte) 0, addCoin);
				if (logMission != 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, "UpdatePlayerToDatabase : UpdateDailyMission SP Error.");
				}
				
				Integer logRoom = DBManager.getInstance().InsertLogInRoomBalance(_roomID, playerID, addCoin, BigInteger.valueOf(newCoin));
				if(logRoom != 0)
				{
					LogMessage(ExtensionLogLevel.ERROR, "UpdatePlayerToDatabase : InsertLogInRoomBalance Sp Error.");
				}
			}
			catch (Exception e)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("UpdatePlayerToDatabase : %s", ErrorClass.StackTraceToString(e)));
			}
		}
	}
	
	private class CheckUserAchievement implements Runnable
	{
		final int playerID;
		final int category;
		final int value;
		
		public CheckUserAchievement(int checkPlayer, int checkCategory, int checkValue)
		{
			playerID = checkPlayer;
			category = checkCategory;
			value = checkValue;
		}
		
		public void run()
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
						LogMessage(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : InsertUserAchivement Player %d, achievementID %d Failed.", playerID, achievementID));
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
							LogMessage(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : InsertUserGift Player %d, achievementID %d Failed.", playerID, achievementID));
						}
		        	}
				}
			}
			catch(Exception e)
			{
				LogMessage(ExtensionLogLevel.ERROR, String.format("CheckUserAchievement : %s.", ErrorClass.StackTraceToString(e)));
			}
		}
	}
}
