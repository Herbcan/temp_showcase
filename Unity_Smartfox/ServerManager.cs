using UnityEngine;
using Holoville.HOTween;

using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

using System;
using System.Collections;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.IO;
using System.Text;

using Sfs2X;
using Sfs2X.Core;
using Sfs2X.Util;
using Sfs2X.Entities;
using Sfs2X.Requests;
using Sfs2X.Entities.Data;


public enum AccountType
{
    GameServer,
    Facebook,
    GoogleAccount,
    Guest
}

public class RequestData
{
    public JObject CachedSendObject;
    public WWWForm CachedForm;
    public WWW CachedWebServer;
}

public class winnerChipData
{
    public byte aniPlay = 0;

    public float aniTime = 0f;

    public long winChips = 0L;
    public long winMaxChips = 0L;
}

public class ServerManager : MonoBehaviour
{
    GoogleCloudMessageService GCMService = null;
    public static ServerManager m_instance = null;
    public static ServerManager Instance
    {
        get
        {
            if (m_instance == null)
            {
                GameObject g = new GameObject("Server Manager");
                DontDestroyOnLoad(g);
                m_instance = g.AddComponent<ServerManager>();
                m_instance.serverResponseReceived = new Dictionary<string, bool>();
                m_instance._requests = new List<QueuedRequest>();
            }

            return m_instance;
        }
    }


    private bool m_nowLauncher = true;
    public static bool nowLauncher
    {
        get { return Instance.m_nowLauncher; }
        set { Instance.m_nowLauncher = value; }
    }


#if UNITY_STANDALONE
    const string PLATFORM = "pc";
#elif UNITY_ANDROID
    const string PLATFORM = "android";
#elif UNITY_IOS
    const string PLATFORM = "IOS";
#elif UNITY_WEBPLAYER
    const string PLATFORM = "WEB";
#else
    const string PLATFORM = "other";
#endif


    public Version clientVersion
    {
        get
        {
#if UNITY_STANDALONE
            return new Version(0, 9, 1);
#elif UNITY_ANDROID
			return new Version(0, 9, 1);
#elif UNITY_IOS
            return new Version(1, 1, 1);
#elif UNITY_WEBPLAYER
            return new Version(1, 1, 1);
#else
            return new Version(0, 0, 0);
#endif
        }
    }

    //Dictionary<string, bool> serverResponseReceived;
    private Dictionary<string, bool> serverResponseReceived;

    public bool IsLogin = false;

    public int PlayerID;
    private string Token;
    public string token
    {
        get
        {
            return Token;
        }
    }
    private string TempToken;

    public UserProfileData PlayerData;
    public Texture2D PlayerPhoto;

    public SmartFox GameSmartFox;
    private User _gameUser;
    public SmartFox MServer;

    public int CurrentRoomKey;
    public int ReconnectAttempt;
    public const int MAX_RECONNECT_ATTEMPT = 5;
    public List<int> RestrictedInviteID;
    public List<int> BlockedChatID;
    public int UnreadMessage;
    public int FriendRequest;

    public long LastServerTime;
    public float LastLocalTime;
    public bool isWaitingForServerResponse;
    public bool isWaitingForReServerResponse = false;

    public string registeredDeviceIDforPushMessage = "";

    public string loginOS = "other";
    public string gcmKey = "-";
    public string apnsKey = "-";

    public string gameInternalCode = "a02";//보드 카테고리

    //FIXME: delete
    public string testMessage;

    //Reconnect 
    ConnectionState _currentConnectionState = ConnectionState.Connected;
    //List<QueuedRequest> _requests;
    List<QueuedRequest> _requests;

    public string MasterLocation;
    public string GameHost;

    bool _stopReconnect = false;

    #region ServerEvent
    public Queue<RequestData> CachedRequest;
    public JObject CachedSendObject;
    public WWWForm CachedForm;
    public WWW CachedWebServer;
    public delegate void OnResponseReceived(string JsonData);
    public event OnResponseReceived CachedResponse;
    public event OnResponseReceived SpecialCaseDelegate;

    //Auth
    public event OnResponseReceived OnServerError;
    public event OnResponseReceived HandleLogin;
    public event OnResponseReceived OnServerLogin;

    //GameData
    public event OnResponseReceived OnGetUserData;
    public event OnResponseReceived OnProfileNickEdit;
    public event OnResponseReceived OnProfilePerEdit;
    public event OnResponseReceived OnGetPlayerData;
    public event OnResponseReceived OnChangeProfilePicture;
    public event OnResponseReceived OnGetUserProperty;
    public event OnResponseReceived OnGetPlayerProperty;
    public event OnResponseReceived OnGetUserPropertyList;
    public event OnResponseReceived OnListAllMyFriends;
    public event OnResponseReceived OnGetSpecialEventNumbers;
    public event OnResponseReceived OnGetUseDailySpinResult;
    public event OnResponseReceived OnGetUseDailySpinResultFailed;
    public event OnResponseReceived OnSellProperty;
    public event OnResponseReceived OnListMyGift;
    public event OnResponseReceived OnAcceptGift;
    public event OnResponseReceived OnGetMyGirlList;
    public event OnResponseReceived OnChangeActiveGirl;
    public event OnResponseReceived OnLoadAllMessages;
    public event OnResponseReceived OnComposeMessageByNickname;
    public event OnResponseReceived OnLoadBlockList;
    public event OnResponseReceived OnReadMessage;
    public event OnResponseReceived OnDeleteMessages;
    public event OnResponseReceived OnBlockPlayer;
    public event OnResponseReceived OnUnblockPlayer;
    public event OnResponseReceived OnUseVoucherSuccess;
    public event Action OnUseVoucherFailed;
    public event OnResponseReceived OnCheckClientVersion;

    public Action<BillingResult> OnGoogleInAppFailedConsume;
    public Action<BillingResult> OnGoogleInAppSuccessConsume;
    public Action OnSignInSuccess;

	public event OnResponseReceived OnIOSBilllingSuccess;
    public event OnResponseReceived OnIOSBilllingFailed;

    public event OnResponseReceived OnGetPlayerList;
    public event OnResponseReceived OnGetFriendRequest;
    public event OnResponseReceived OnGetFriendRequestList;
    public event OnResponseReceived OnGetFriendList;

    public event OnResponseReceived OnGetSearchFriendResult;

    public event OnResponseReceived OnProfilePictureUpdate;
    public event OnResponseReceived OnCompleteSendGift;

    public event OnResponseReceived OnGetAchievementList;

    public event OnResponseReceived OnInputReferralCodeSuccess;

    //private string AuthServerLocation = "http://banting.diamondgames.net:3930/auth.php";
	private string AuthServerLocation = "http://128.199.81.224:8081/auth.php";
    
    private string GameDataServerLocation = "";
    private string GameDataServerPort = ":8081/GameData.php";

    private string ImageServerLocation = "";
    private string ImageServerPort = ":8082/Upload.php";

    public string ImageURL = "";
	private string ImageURLPort = ":8082/images/pp/";

    private string cachedServerLocation = "";

    public float pingCheckTime = 0f;
    public float fetchCheckTime = 0f;

	public bool chkPopup = false;

    private int currentRetryCountGetProfilePicture = 0;
    const int MAX_RETRY_COUNT = 3;

    public bool IsRegistered
    { 
        get
        {
            return PlayerPrefs.HasKey("CapsaSaveData");
        }
    }

    public AccountType accountType
    {
        get
        {
            if (PlayerPrefs.HasKey("AccountType"))
            {
                return (AccountType)PlayerPrefs.GetInt("AccountType");
            }

            return AccountType.GameServer;
        }
        set
        {
            PlayerPrefs.SetInt("AccountType", (int)value);
        }
    }

    public void changeUrl(string URL)
    {
		AuthServerLocation = "http://" + URL + ":8081/auth.php";
        //AuthServerLocation = "http://" + URL + ":3930/auth.php";
    }

    public void SetupWebServerAdress(string ip)
    {
        GameDataServerLocation = "http://" + ip + GameDataServerPort;
        ImageServerLocation = "http://" + ip + ImageServerPort;
       // ImageURL = "http://" + ip + ImageURLPort;
    }

    public void SendServerError(string code, List<string> message)
    {
        ErrorReport temp = new ErrorReport();
        temp.ErrorCode = code;
        temp.ErrorMessage = message;

        if (OnServerError != null)
            OnServerError(JsonConvert.SerializeObject(temp));


    }

    void OnQuitApplication()
    {
        Destroy(gameObject);
    }

    void OnDestory()
    {
        m_instance = null;
    }

    void Awake()
    {
        if (m_instance == null)
        {
            m_instance = this;

            DontDestroyOnLoad(gameObject);
        }
        else
        {
            Destroy(gameObject);

            return;
        }

        switch (Application.platform)
        {
            case RuntimePlatform.Android:
                loginOS = "android";
                break;
            case RuntimePlatform.IPhonePlayer:
                loginOS = "IOS";
                break;
            case RuntimePlatform.WindowsPlayer:
            case RuntimePlatform.WindowsEditor:
            case RuntimePlatform.OSXEditor:
            case RuntimePlatform.OSXPlayer:
                loginOS = "pc";
                break;
            case RuntimePlatform.WindowsWebPlayer:
            case RuntimePlatform.OSXWebPlayer:
                loginOS = "WEB";
                break;
            default:
                break;
        }
    }

    public void Start()
    {
        m_instance.RestrictedInviteID = new List<int>();
        m_instance.BlockedChatID = new List<int>();

        //m_instance.OnServerLogin += AfterLogin;
        //StartCoroutine(m_instance.HandleRequests());
    }
    #endregion

    #region Encryption
    private string _defaultToken = "1DnaCdBl3E++DNPSrbucTAhKJkwLtmi4a+ATcPbqpBk=Df6j9fCBE1918cRLzKNQGw==";

    private string Md5Sum(string strToEncrypt)
    {
        System.Text.UTF8Encoding ue = new System.Text.UTF8Encoding();
        byte[] bytes = ue.GetBytes(strToEncrypt);
        // encrypt bytes
        System.Security.Cryptography.MD5CryptoServiceProvider md5 = new System.Security.Cryptography.MD5CryptoServiceProvider();
        byte[] hashBytes = md5.ComputeHash(bytes);
        // Convert the encrypted bytes back to a string (base 16)
        string hashString = "";
        for (int i = 0; i < hashBytes.Length; i++)
        {
            hashString += System.Convert.ToString(hashBytes[i], 16).PadLeft(2, '0');
        }
        return hashString.PadLeft(32, '0');
    }

    // Generate Random Key & IV
    private string GenerateRandomKeys()
    {
        byte[] Key = new byte[256 / 8];
        byte[] IV = new byte[128 / 8];
        RNGCryptoServiceProvider random = new RNGCryptoServiceProvider();
        random.GetBytes(Key);
        random.GetBytes(IV);
        return string.Format("{0}{1}", Convert.ToBase64String(Key), Convert.ToBase64String(IV));
    }

    // Encrypt AES
    private string EncryptAES(string Message, string Key, string IV)
    {
        try
        {
            RijndaelManaged aes = new RijndaelManaged();
            aes.Padding = PaddingMode.PKCS7;
            aes.Mode = CipherMode.CBC;
            aes.KeySize = 256;
            aes.Key = Convert.FromBase64String(Key);
            aes.IV = Convert.FromBase64String(IV);
            ICryptoTransform encryptor = aes.CreateEncryptor(aes.Key, aes.IV);
            MemoryStream msEncrypt = new MemoryStream();
            CryptoStream csEncrypt = new CryptoStream(msEncrypt, encryptor, CryptoStreamMode.Write);
            StreamWriter swEncrypt = new StreamWriter(csEncrypt);
            swEncrypt.Write(Message);
            swEncrypt.Close();
            csEncrypt.Close();
            aes.Clear();
            return Convert.ToBase64String(msEncrypt.ToArray());
        }
        catch (Exception ex)
        {
            throw new CryptographicException("Server Manager Error trying to encrypt : ", ex);
        }
    }

    // Decrypt AES
    private string DecryptAES(string Message, string Key, string IV)
    {
        try
        {
            RijndaelManaged aes = new RijndaelManaged();
            aes.Padding = PaddingMode.PKCS7;
            aes.Mode = CipherMode.CBC;
            aes.KeySize = 256;
            aes.Key = Convert.FromBase64String(Key);
            aes.IV = Convert.FromBase64String(IV);
            ICryptoTransform decryptor = aes.CreateDecryptor(aes.Key, aes.IV);
            MemoryStream msDecrypt = new MemoryStream(Convert.FromBase64String(Message));
            CryptoStream csDecrypt = new CryptoStream(msDecrypt, decryptor, CryptoStreamMode.Read);
            StreamReader srDecrypt = new StreamReader(csDecrypt);
            string plaintext = srDecrypt.ReadToEnd();
            srDecrypt.Close();
            csDecrypt.Close();
            msDecrypt.Close();
            aes.Clear();
            return plaintext;
        }
        catch (Exception ex)
        {
            throw new CryptographicException("Server Manager Error trying to decrypt : ", ex);
        }
    }
    #endregion

    public void StartSFServer()
    {
        Security.PrefetchSocketPolicy(GameHost, 443, 500);

        // Untuk Sementara
        BlockedChatID.Clear();
        ConfigData GameServerConfig = new ConfigData();
        GameServerConfig.Host = GameHost;
        GameServerConfig.Port = 443;
        GameServerConfig.HttpPort = 8080;
        GameServerConfig.BlueBoxPollingRate = 300;
        GameServerConfig.UseBlueBox = false;
		GameServerConfig.Zone = "SusunGameExtension";

        GameSmartFox = new SmartFox(false);
        GameSmartFox.AddEventListener(SFSEvent.CONNECTION, OnGameConnection);
        GameSmartFox.AddEventListener(SFSEvent.CONNECTION_LOST, OnGameConnectionLost);
        GameSmartFox.AddEventListener(SFSEvent.LOGIN, OnGameLogin);
        GameSmartFox.AddEventListener(SFSEvent.LOGIN_ERROR, OnGameLoginError);
        GameSmartFox.AddEventListener(SFSEvent.LOGOUT, OnGameLogout);
        GameSmartFox.AddEventListener(SFSEvent.ROOM_JOIN_ERROR, JoinRoomFailed);

        GameSmartFox.Connect(GameServerConfig);
        
		//Debug.Log("Connecting to SFS server");
    }

    public void StartMasterServer(string masterLocation)
    {
		//Debug.Log (masterLocation);
        Security.PrefetchSocketPolicy(masterLocation, 443, 500);

        MasterLocation = masterLocation;
        ConfigData GameServerConfig = new ConfigData();
        GameServerConfig.Host = masterLocation;
        GameServerConfig.Port = 443;
        GameServerConfig.HttpPort = 8080;
        GameServerConfig.BlueBoxPollingRate = 300;
        GameServerConfig.UseBlueBox = false;
		GameServerConfig.Zone = "SusunMasterExtension";
		
		MServer = new SmartFox(false);
        MServer.AddEventListener(SFSEvent.CONNECTION, OnMasterConnection);
        MServer.AddEventListener(SFSEvent.CONNECTION_LOST, OnMasterConnectionLost);
        MServer.AddEventListener(SFSEvent.LOGIN, OnMasterLogin);
        MServer.AddEventListener(SFSEvent.LOGIN_ERROR, OnMasterLoginError);
        MServer.AddEventListener(SFSEvent.LOGOUT, OnMasterLogout);
        MServer.AddEventListener(SFSEvent.ADMIN_MESSAGE, OnAdminMessage);
        MServer.Debug = true;

        MServer.Connect(GameServerConfig);

		//Debug.Log("Connecting to Master server");
    }

    public void OnAdminMessage(BaseEvent evt)
    {
        //Debug.log("Admin message: " + (string)evt.Params["message"]);

        PopupManager.ShowServerAnnouncementPopup((string)evt.Params["message"]);
    }

    void Update()
    {
        //게임서버 핑 체크
        if (this.pingCheckTime > 0f)
        {
            this.pingCheckTime -= Time.smoothDeltaTime;
            if (this.pingCheckTime <= 0f)
            {
                this.pingCheckTime = 0f;
                this.fetchCheckTime = 15f;

                if (PopupManager.isShowPopups("RetryConnectionPopup") == false && PopupManager.isShowPopups("RetryFetchPopup") == false){
					PopupManager.ShowLoadingPopup("Fetching...");
				 	this.chkPopup = true;
				}
                    

                //if (!isWaitingForServerResponse)
                //  StartCoroutine("WaitForServerResponse", 0.1f);
            }
        }

        if (this.fetchCheckTime > 0f)
        {
            this.fetchCheckTime -= Time.smoothDeltaTime;
            if (this.fetchCheckTime <= 0f)
            {
                this.fetchCheckTime = 0f;

                /*Popup p = PopupManager.GetPopup("LoadingPopup");
                if (p != null)
                    p.Hide();*/

                /*WebViewPopup w = (WebViewPopup)PopupManager.GetPopup("WebViewPopup");
                if (w != null)
                    w.OnClose();*/

                PopupManager.HidePopups("LoadingPopup", "WebViewPopup");

                if (PopupManager.isShowPopups("RetryConnectionPopup") == false)
                    PopupManager.ShowPopup("RetryFetchPopup");
            }
        }
    }

    void FixedUpdate()
    {
        if (GameSmartFox != null)
            GameSmartFox.ProcessEvents();
       
        if (MServer != null)
            MServer.ProcessEvents();
    }

    public void OnApplicationQuit()
    {
#if UNITY_STANDALONE
        Application.CancelQuit();

        PopupManager.ShowPopup("ExitPopup");
#else

        if (GameSmartFox != null)
        {
            GameSmartFox.RemoveAllEventListeners();
            GameSmartFox.Disconnect();
        }

        if (MServer != null)
        {
            MServer.RemoveAllEventListeners();
            MServer.Disconnect();
        }
#endif
    }

    IEnumerator QuitApplicationConfirm()
    {
        Popup popup = PopupManager.ShowPopup("ExitPopup");

        yield return new WaitForEndOfFrame();

        while (popup.isShow)
        {
            yield return new WaitForEndOfFrame();
        }
    }

    //public void RestartGameServer()
    //{
    //    PopupManager.ShowLoadingPopup();
    //    CapsaUtilities.CallDelayedMethod(10f, delegate()
    //    {
    //        if (!GameSmartFox.IsConnected && !GameSmartFox.IsConnecting)
    //        {
    //            if (SceneManager.CurrentScene.GetType() == typeof(RoomScene))
    //                GameManager.Instance.RestartGame();
    //            else
    //                SceneManager.LoadScene("RoomScene");
    //            GameSmartFox = null;
    //            StartSFServer(GameHost);
    //        }
    //    });
    //}

    public void RestartMasterServer()
    {
		//Debug.Log ("Popup Status: " + this.chkPopup);
        //ReconnectAttempt++;
		if (this.chkPopup == false) {
			PopupManager.HideAllPopups ();
			PopupManager.ShowLoadingPopup ();
		} else {
			this.chkPopup = false;
		}

        this.pingCheckTime = 0f;
        this.fetchCheckTime = 0f;

        if (MServer != null)
        {
            if (MServer.IsConnected)
            {
                MServer.RemoveAllEventListeners();
                MServer.Disconnect();
            }
        }
		/*
        if (GameSmartFox != null)
        {
            if (GameSmartFox.IsConnected)
            {
                GameSmartFox.RemoveAllEventListeners();
                GameSmartFox.Disconnect();
            }
        }
        */

        //CapsaUtilities.CallDelayedMethod(2f, delegate()
        //{
        if (!MServer.IsConnected && !MServer.IsConnecting)
        {
            //MServer = null;
			StartCoroutine("ReconnectServer");
            //StartMasterServer(MasterLocation);
        }
        //});
		/*
        if (!isWaitingForReServerResponse)
            StartCoroutine("ReMasterServerResponse", 15.0f);
       */
    }

	public IEnumerator ReconnectServer()
	{
		float reconnectTime = 34.0f;
		float startTime = 0.0f;
		int i = 0;

		while (startTime <= reconnectTime)
		{
			if (!MServer.IsConnected && !MServer.IsConnecting)
			{
				//Debug.Log("Try Reconnect!!");
				MServer.RemoveAllEventListeners();
				StartMasterServer(MasterLocation);
			} else {
				startTime = reconnectTime;
			}

			if(i == 34){
				PopupManager.ShowPopup("RetryConnectionPopup");
			}
			yield return new WaitForSeconds(1.0f);
			i++;
			startTime += 1.0f;
		}
	}

    //call this whenever make transaction
    public void SyncUserData()
    {
        MServer.Send(new ExtensionRequest(ServerConstant.UPDATE_USER_DATA, new SFSObject()));
    }

    public void KeepGameServerAlive()
    {
        if (GameSmartFox != null)
        {
            if (GameSmartFox.IsConnected)
            {
                GameSmartFox.Send(new ExtensionRequest(ServerConstant.KEEP_ALIVE, new SFSObject()));
                Invoke("KeepGameServerAlive", 20);
                ////Debug.log("ping");

                RestrictedInviteID.Clear();
            }
        }
    }

    public void KeepMasterServerAlive()
    {
        if (MServer != null)
        {
            if (MServer.IsConnected)
            {
                MServer.Send(new ExtensionRequest(ServerConstant.KEEP_ALIVE, new SFSObject()));
                Invoke("KeepMasterServerAlive", 200);
                ////Debug.log("ping");
            }
        }
    }

    #region EventHandler
    void JoinRoomFailed(BaseEvent evt)
    {
        PopupManager.ShowNotificationPopup("Sorry! Unknown error has been occurred. Please try again later.");
        CapsaUtilities.CallDelayedMethod(3f, () => SceneManager.LoadScene("MainMenuScene"));
    }

    public void OnGameConnection(BaseEvent evt)
    {
        bool success = (bool)evt.Params["success"];
        string error = (string)evt.Params["errorMessage"];
        if (success)
        {
            PopupManager.HideAllPopups();

            //StopCoroutine("ReMasterServerResponse");

           // PopupManager.HidePopups("LoadingPopup");
            //Debug.Log("GameServer Connect Success!!");
            GameSmartFox.Send(new LoginRequest(PlayerID.ToString(), Token));
            ////Debug.log("Trying to login");
        }
        //else
        //{
			//Debug.Log("GameServer Connect Failed, error : " + error);
            //retry to connect
            //if (ReconnectAttempt < MAX_RECONNECT_ATTEMPT)
            //    RestartMasterServer();
            //else
            //    PopupManager.ShowPopup("RetryConnectionPopup");
           // if (!isWaitingForServerResponse)
              //  StartCoroutine("WaitForServerResponse", 1.0f);
        //}

    }

    public void OnMasterConnection(BaseEvent evt)
    {
        bool success = (bool)evt.Params["success"];
        string error = (string)evt.Params["errorMessage"];

        if (success)
        {
            PopupManager.HideAllPopups("ReferralPopup(Clone)");

            //StopCoroutine("ReMasterServerResponse");

            //Debug.Log("MasterServer Connect Success!!");
            MServer.Send(new LoginRequest(PlayerID.ToString(), Token));
        }
        else
        {
            //Debug.Log("MasterServer Connect Failed, error : " + error);
            //retry to connect
            //if (ReconnectAttempt < MAX_RECONNECT_ATTEMPT)
            //    RestartMasterServer();
            //else
            //    PopupManager.ShowPopup("RetryConnectionPopup");
           // if (!isWaitingForServerResponse)
               // StartCoroutine("WaitForServerResponse", 1.0f);
        }
    }

    public void OnGameConnectionLost(BaseEvent evt)
    {
        //Debug.Log("Game Connection lost; reason: " + (string)evt.Params["reason"]);
        //PopupManager.ShowNotificationPopup("Game Connection lost; reason: " + (string)evt.Params["reason"]);

        if (_stopReconnect)
        {
            return;
        }

#if _TEST
        if (!isWaitingForServerResponse)
            StartCoroutine("WaitForServerResponse", 1.0f);
#endif
        if (ServerManager.Instance.GameSmartFox.IsConnecting)
        {
            //PopupManager.ShowLoadingPopup();

            this.pingCheckTime = 0f;
            this.fetchCheckTime = 0f;

            if (GameSmartFox != null)
            {
                if (GameSmartFox.IsConnected)
                {
                    GameSmartFox.RemoveAllEventListeners();
                    GameSmartFox.Disconnect();
                }

                StartSFServer();
            }
        }

        ////retry to connect
        //if (ReconnectAttempt < MAX_RECONNECT_ATTEMPT)
        //    RestartMasterServer();
        //else
        //    PopupManager.ShowPopup("RetryConnectionPopup");
    }

    public void OnMasterConnectionLost(BaseEvent evt)
    {
       //Debug.Log("Master Server Connection lost; reason: " + (string)evt.Params["reason"]);
		//Debug.Log("Master Server Connection lost");

        //PopupManager.ShowNotificationPopup("Game Connection lost; reason: " + (string)evt.Params["reason"]);
        if (_stopReconnect)
        {
            PopupManager.ShowPopup("LoggedOnAnotherDevicePopup");

            return;
        }

		//Debug.Log ("Master Connection Lost!!");
        RestartMasterServer();

        //if (!isWaitingForServerResponse)
          //  StartCoroutine("WaitForServerResponse", 1.0f);

        ////retry to connect
        //if (ReconnectAttempt < MAX_RECONNECT_ATTEMPT)
        //    RestartMasterServer();
        //else
        //    PopupManager.ShowPopup("RetryConnectionPopup");
    }

    public void OnGameLogin(BaseEvent evt)
    {
        // Make sure we got in and then populate the room list string array
        ////Debug.log("Logged in successfully");

        _gameUser = (User)evt.Params["user"];
        GameSmartFox.AddEventListener(SFSEvent.EXTENSION_RESPONSE, OnExtensionResponse);

        ISFSObject so = new SFSObject();
        so.PutInt(ServerConstant.ROOM_KEY, CurrentRoomKey);
        SendGameRequest(new ExtensionRequest(ServerConstant.JOIN_ROOM, so));
        Invoke("KeepGameServerAlive", 20);
    }

    public void OnMasterLogin(BaseEvent evt)
    {
       // Make sure we got in and then populate the room list string array
        ////Debug.log("Logged in successfully");

        MServer.AddEventListener(SFSEvent.EXTENSION_RESPONSE, OnMasterExtensionResponse);

        Invoke("KeepMasterServerAlive", 200);

        StartCoroutine(CheckUnfinishedGame());
    }

    public IEnumerator CheckUnfinishedGame()
    {
        while (SceneManager.CurrentScene == null)
        {
            yield return new WaitForSeconds(0.5f);
        }

        //check if the user is currently in room

        //PopupManager.ShowLoadingPopup();
        MServer.Send(new ExtensionRequest(ServerConstant.CHECK_IS_IN_GAME_ROOM, new SFSObject()));
    }

    public void OnGameLoginError(BaseEvent evt)
    {
        //Debug.log("Login error: " + (string)evt.Params["errorMessage"]);
    }

    public void OnMasterLoginError(BaseEvent evt)
    {
        //Debug.log("Master Login error: " + (string)evt.Params["errorMessage"]);

        if ((short)evt.Params["errorCode"] == 6)
        {
            Invoke("ReconnectLogin", 1.5f);
        }
    }

    void ReconnectLogin()
    {
        ////Debug.log("MasterServer Connect Success");
        MServer.Send(new LoginRequest(PlayerID.ToString(), Token));
        ////Debug.log("Trying to login");
    }

    void OnGameLogout(BaseEvent evt)
    {
        ////Debug.log("OnLogout");
    }

    void OnMasterLogout(BaseEvent evt)
    {
        ////Debug.log("OnLogout");
    }

    public void SendGameRequest(ExtensionRequest request)
    {
        GameSmartFox.Send(request);

        //if (!isWaitingForServerResponse)
          //  StartCoroutine("WaitForServerResponse", 7.0f);
    }

    public void SendMasterRequest(ExtensionRequest request)
    {
        MServer.Send(request);

        //if (!isWaitingForServerResponse)
        //  StartCoroutine("WaitForServerResponse", 7.0f);
    }

    public IEnumerator ReMasterServerResponse(float waitTimeSec)
    {
        isWaitingForReServerResponse = true;

        yield return new WaitForSeconds(waitTimeSec);

        PopupManager.ShowLoadingPopup();

        if (MServer != null)
        {
            if (MServer.IsConnected)
            {
                MServer.RemoveAllEventListeners();
                MServer.Disconnect();
            }
        }

        if (GameSmartFox != null)
        {
            if (GameSmartFox.IsConnected)
            {
                GameSmartFox.RemoveAllEventListeners();
                GameSmartFox.Disconnect();
            }
        }

        if (!MServer.IsConnected && !MServer.IsConnecting)
        {
            MServer = null;
            StartMasterServer(MasterLocation);
        }
       
        if (!isWaitingForServerResponse)
            StartCoroutine("WaitForServerResponse", 25.0f);

        isWaitingForReServerResponse = false;
    }

    public IEnumerator WaitForServerResponse(float waitTimeSec)
    {
        isWaitingForServerResponse = true;

        yield return new WaitForSeconds(waitTimeSec);

        this.pingCheckTime = 0f;
        this.fetchCheckTime = 0f;

        //retry to connect
        //if (ReconnectAttempt < MAX_RECONNECT_ATTEMPT)
        //    RestartMasterServer();
        //else
        //    PopupManager.ShowPopup("RetryConnectionPopup");

        /*WebViewPopup w = (WebViewPopup)PopupManager.GetPopup("WebViewPopup");
        if (w != null)
            w.OnClose();*/

        string[] hidPop = { "LoadingPopup", "RetryFetchPopup", "WebViewPopup" };
        PopupManager.HidePopups(hidPop);

        PopupManager.ShowPopup("RetryConnectionPopup");
     
        isWaitingForServerResponse = false;
    }

    public void OnExtensionResponse(BaseEvent evt)
    {
        string cmd = (string)evt.Params["cmd"];
        SFSObject sfsObj = (SFSObject)evt.Params["params"];

        if(cmd.Equals("TestTick"))
        {
            int counter = sfsObj.GetInt("NO");
            //GameManager.Instance.PingAnimation();

            string[] hidPop = { "LoadingPopup", "RetryFetchPopup", "RetryConnectionPopup" };
            PopupManager.HidePopups(hidPop);

            this.fetchCheckTime = 0f;
            this.pingCheckTime = 7f;//7초

        }
        else
        {
            isWaitingForServerResponse = false;
		
			switch (cmd){
			case ServerConstant.USER_ENTER_ROOM:
				GameResponse.enterUserRoom(sfsObj);
				break;
			case ServerConstant.JOIN_ROOM:
				GameResponse.joinRoom(sfsObj);
				break;
			case ServerConstant.WAIT_FOR_OTHER_USER:
				GameResponse.waitForOtherUser();
				break;
			case ServerConstant.ROOM_INFORMATION:
				GameResponse.roomInfomation(sfsObj);
				break;
			case ServerConstant.CURRENT_GAME_INFO:
				GameResponse.currentGameInfo(sfsObj);
				break;
			case ServerConstant.USER_LEAVE_ROOM:
				GameResponse.userLeaveRoom(sfsObj);
				break;
			case ServerConstant.COUNTDOWN_START_GAME:
				GameResponse.countDownStartGame(sfsObj);
				break;
			case ServerConstant.COUNTDOWN_PLAY_GAME:
				GameResponse.countDownPlayGame(sfsObj);
				break;
			case ServerConstant.SHUFFLE_CARD:
				GameResponse.ShuffleCard(sfsObj);
				break;
			case ServerConstant.RESORT_CARD:
				GameResponse.ReSortCard(sfsObj);
				break;
			case ServerConstant.GAME_RESULT:
				GameResponse.GameResult(sfsObj);
				break;
//			case ServerConstant.KEEP_ALIVE:
//				StartCoroutine(KeepGameServerAlive(ServerConstant._pingponginterval));
//				break;
			}
		
            switch (cmd)
            {
//                case ServerConstant.USER_ENTER_ROOM:
//                    {
//                        GameRecv.enterUserRoom(sfsObj);
//                    }
//                    break;
//                case ServerConstant.WAIT_FOR_OTHER_USER:
//                    {
//                        GameRecv.waitForOthderUser();
//                    }
//                    break;
//                case ServerConstant.ROOM_INFORMATION:
//                    {
//                        GameRecv.roomInfomation(sfsObj);
//                    }
//                    break;
//                case ServerConstant.USER_LEAVE_ROOM:
//                    {
//                        GameRecv.userLeaveRoom(sfsObj);
//                    }
//                    break;
//                case ServerConstant.COUNTDOWN_START_GAME:
//                    {
//                        GameRecv.countDownStartGame(sfsObj);
//                    }
//                    break;
//                case ServerConstant.SHUFFLE_CARD:
//                    {
//                        GameRecv.shuffleCard(sfsObj);
//                    }
//                    break;
//                case ServerConstant.MISSION:
//                    {
//                        GameRecv.mission(sfsObj);
//                    }
//                    break;
//                case ServerConstant.GAIN_MISSION:
//                    {
//                        GameRecv.gainMission(sfsObj);
//                    }
//                    break;
//                case ServerConstant.GET_SPECIAL_HAND:
//                    {
//                        GameRecv.getSpecialHand(sfsObj);
//                    }
//                    break;
//                case ServerConstant.NEW_TURN:
//                    {
//                        GameRecv.newTurn(sfsObj);
//                    }
//                    break;
//                case ServerConstant.PLAY_CARD:
//                    {
//                        GameRecv.playCard(sfsObj);
//                    }
//                    break;
//                case ServerConstant.PASS_TURN:
//                    {
//                        GameRecv.passTurn(sfsObj);
//                    }
//                    break;
//                case ServerConstant.DRAGON_RESULT:
//                case ServerConstant.GAME_RESULT:
//                    {
//                        GameRecv.gameResult(sfsObj);
//                    }
//                    break;
//                case ServerConstant.BANKRUPT_LEAVE:
//                    {
//                        GameRecv.bankruptLeave();
//                    }
//                    break;
//                case ServerConstant.JOIN_ROOM:
//                    {
//                        GameRecv.joinRoom(sfsObj);
//                    }
//                    break;
//                case ServerConstant.CURRENT_GAME_INFO:
//                    {
//                        GameRecv.currentGameInfo(sfsObj);
//                    }
//                    break;
                case ServerConstant.BUY_DECO:
                    {
                        GameResponse.buyDeco(sfsObj);
                    }
                    break;
                case ServerConstant.GET_DECO:
                    {
						GameResponse.getDeco(sfsObj);
                    }
                    break;
            case ServerConstant.GET_ROOM_MESSAGE:
            {
                GameResponse.getRoomMessage(sfsObj);
            }
            break;
            case ServerConstant.GET_ROOM_EXPRESION:
            {
				GameResponse.getRoomExpresion(sfsObj);
            }
            break;
			case ServerConstant.INVITE_GET_USERS:
			{
				GameResponse.GetUser(sfsObj);
			}
			break;
			case ServerConstant.INVITE_TO_GAME:
			{
				GameResponse.InviteToGame(sfsObj);
			}
			break;
		}
        }
    }

    public void OnMasterExtensionResponse(BaseEvent evt)
    {

        string cmd = (string)evt.Params["cmd"];
        SFSObject dataObject = (SFSObject)evt.Params["params"];

        switch (cmd)
        {
            case ServerConstant.ROOM_TO_ENTER:
                {
                    //                    //Debug.log("ROOM TO ENTER");

                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        //PopupManager.HidePopups("LoadingPopup");

                        string errorCode = dataObject.GetUtfString(ServerConstant.ERROR_CODE);
                        //unfinished game
                        if (errorCode == "E701")
                        {
                            int roomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);
                            CurrentRoomKey = roomKey;
                            string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                            GameHost = host;

                            //Debug.log(errorCode);
                            NotificationPopup p = PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]) as NotificationPopup;
                            p.SetButton(delegate()
                            {
                                SceneManager.LoadScene("RoomScene");
                                StartSFServer();
                            });
                        }
                        else
                        {
                            //Debug.log(errorCode);
                            PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                        }
                    }
                    else
                    {
                        CapsaUtilities.CallDelayedMethod(2f, delegate()
                        {
                            SceneManager.LoadScene("RoomScene");
                            CurrentRoomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);
                            string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                            GameHost = host;

                            StartSFServer();
                        });
                    }
                }
                break;
            case ServerConstant.FIND_ROOM:
                {
                    //                    //Debug.log("FIND ROOM");

                    //PopupManager.HidePopups("LoadingTransactionPopup");
                    FindRoomScene fRoomScene = SceneManager.CurrentScene as FindRoomScene;
                    if (fRoomScene == null)
                        break;

                    fRoomScene.LoadingCircle.SetActive(false);

                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        string errorCode = dataObject.GetUtfString(ServerConstant.ERROR_CODE);
                        //unfinished game
                        if (errorCode == "E701")
                        {
                            int roomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);
                            CurrentRoomKey = roomKey;
                            string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                            GameHost = host;

                            //Debug.log(errorCode);
                            NotificationPopup p = PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]) as NotificationPopup;
                            p.SetButton(delegate()
                            {
                                SceneManager.LoadScene("RoomScene");
                                StartSFServer();
                            });
                        }
                        else
                        {
                            //Debug.log(errorCode);
                            PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                        }
                    }
                    else
                    {
                        int[] roomKeys = dataObject.GetIntArray(ServerConstant.LIST_ROOM_KEYS);
                        List<TableObject> table = new List<TableObject>();

                        for (int i = 0; i < roomKeys.Length; i++)
                        {
                            ISFSObject roomInfo = dataObject.GetSFSObject(roomKeys[i].ToString());
                            TableObject newTable = new TableObject();
                            newTable.AssignTableValue(roomKeys[i],
                                                      roomInfo.GetUtfString(ServerConstant.GAME_SERVER_HOST),
                                                      roomInfo.GetLong(ServerConstant.BASE_BET),
                                                      roomInfo.GetBool(ServerConstant.HAS_MISSION),
                                                      roomInfo.GetBool(ServerConstant.WINNER_ONLY),
                                                      roomInfo.GetBool(ServerConstant.IS_ROOM_PLAYING),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_1),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_2),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_3),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_1),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_2),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_1),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_2),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_4),
                                                      0, 0, 0, 0, false, false, false, false, 0,
                                                      false, false, false, false);
                            table.Add(newTable);
                        }

                        fRoomScene.CreateTables(table);
                    }
                }
                break;
            case ServerConstant.FIND_ROOM_WITH_RANGE:
                {
                    
			//(SceneManager.CurrentScene as FindRoomScene).LoadingCircle.SetActive(false);
                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        string errorCode = dataObject.GetUtfString(ServerConstant.ERROR_CODE);
                        //unfinished game
                        if (errorCode == "E701")
                        {
                            int roomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);
                            CurrentRoomKey = roomKey;
                            string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                            GameHost = host;

                            Debug.Log(errorCode);
                            NotificationPopup p = PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]) as NotificationPopup;
                            p.SetButton(delegate()
                            {
                                SceneManager.LoadScene("RoomScene");
                                StartSFServer();
                            });
                        }
                        else
                        {
                            Debug.Log(errorCode);
                            PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                        }
                    }
                    else
                    {
                        int[] totalRooms = dataObject.GetIntArray(ServerConstant.TOTAL_ROOMS);
                        // totalRooms[0] -> Grade 500 total room
                        // totalRooms[1] -> Grade 1K total room
                        // totalRooms[2] -> Grade 3K total room
                        // etc
                        // Beginner, Intermidiate, Expert dihitung sendiri dari client

                        FindRoomScene fRoomScene = SceneManager.CurrentScene as FindRoomScene;
                        if (fRoomScene == null)
                            break;

                        fRoomScene.RoomCount(totalRooms);


                        // RoomKeys, sudah terurut. index 0 -> ditampil pertama
                        int[] roomKeys = dataObject.GetIntArray(ServerConstant.LIST_ROOM_KEYS);

                        List<TableObject> table = new List<TableObject>();
                        for (int i = 0; i < roomKeys.Length; i++)
                        {
                            ISFSObject roomInfo = dataObject.GetSFSObject(roomKeys[i].ToString());
                            TableObject newTable = new TableObject();
                            newTable.AssignTableValue(roomKeys[i],
                                                      roomInfo.GetUtfString(ServerConstant.GAME_SERVER_HOST),
                                                      roomInfo.GetLong(ServerConstant.BASE_BET), // Bet value
                                                      roomInfo.GetBool(ServerConstant.HAS_MISSION), // true -> has misson , false -> no mission
                                                      roomInfo.GetBool(ServerConstant.WINNER_ONLY), // true -> winner only, false -> regular
                                                      roomInfo.GetBool(ServerConstant.IS_ROOM_PLAYING), // true -> game started, false -> idle
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_1), // Nickname seat 1
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_2), // etc
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_3),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_1), // PlayerID seat 1
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_2), // etc
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_1), // Gender Seat 1
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_2), // etc
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_1), // Level seat 1
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_2), // etc
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_4),
                                                      roomInfo.GetBool(ServerConstant.IS_HIDDEN_ROOM),
                                                      roomInfo.GetBool(ServerConstant.IS_EXTRA_RULE),
                                                      roomInfo.GetBool(ServerConstant.VIP_ROOM),
                                                      roomInfo.GetBool(ServerConstant.FAST_ROOM),
                                                      roomInfo.GetInt(ServerConstant.MAXIMUM_ROOM_PLAYER),
                                                      roomInfo.GetBool(ServerConstant.VIP_1),
                                                      roomInfo.GetBool(ServerConstant.VIP_2),
                                                      roomInfo.GetBool(ServerConstant.VIP_3),
                                                      roomInfo.GetBool(ServerConstant.VIP_4));
                            table.Add(newTable);
                        }
                        fRoomScene.CreateTables(table);
                    }
                }
                break;

            case ServerConstant.ROOM_AVAILABLE_CHANGED:
                {
                    int betclass = dataObject.GetInt(ServerConstant.ROOM_AVAILABLE_CLASS);
                    int[] totalroom = dataObject.GetIntArray(ServerConstant.TOTAL_ROOM_AVAILABLE);

                }
                break;


            case ServerConstant.ROOM_INFO_CHANGED:
                {
                    int roomkey = dataObject.GetInt(ServerConstant.ROOM_KEY);

                    FindRoomScene fRoomScene = SceneManager.CurrentScene as FindRoomScene;
                    if (fRoomScene == null)
                        break;

                    if (dataObject.GetBool(ServerConstant.IS_ROOM_DELETED))
                    {
                        // room deleted
                        fRoomScene.DeleteRoom(roomkey);
                    }
                    else
                    {   
                        ISFSObject roomInfo = dataObject.GetSFSObject(ServerConstant.ROOM_INFORMATION);

                        fRoomScene.UpdateRoom(roomkey,
                                                      roomInfo.GetUtfString(ServerConstant.GAME_SERVER_HOST),
                                                      roomInfo.GetLong(ServerConstant.BASE_BET),
                                                      roomInfo.GetBool(ServerConstant.HAS_MISSION),
                                                      roomInfo.GetBool(ServerConstant.WINNER_ONLY),
                                                      roomInfo.GetBool(ServerConstant.IS_ROOM_PLAYING),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_1),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_2),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_3),
                                                      roomInfo.GetUtfString(ServerConstant.NICKNAME_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_1),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_2),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.USER_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_1),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_2),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.GENDER_SEAT_4),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_1),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_2),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_3),
                                                      roomInfo.GetInt(ServerConstant.LEVEL_SEAT_4),
                                                      roomInfo.GetBool(ServerConstant.IS_HIDDEN_ROOM),
                                                      roomInfo.GetBool(ServerConstant.IS_EXTRA_RULE),
                                                      roomInfo.GetBool(ServerConstant.VIP_ROOM),
                                                      roomInfo.GetBool(ServerConstant.FAST_ROOM),
                                                      roomInfo.GetInt(ServerConstant.MAXIMUM_ROOM_PLAYER),
                                                      roomInfo.GetBool(ServerConstant.VIP_1),
                                                      roomInfo.GetBool(ServerConstant.VIP_2),
                                                      roomInfo.GetBool(ServerConstant.VIP_3),
                                                      roomInfo.GetBool(ServerConstant.VIP_4));
                    }
                }
                break;
            case ServerConstant.CHECK_IS_IN_GAME_ROOM:
                {
                    //                    //Debug.log("CHECK IS IN GAME ROOM");
                    //ReconnectAttempt = 0;
                    isWaitingForServerResponse = false;
                    StopCoroutine("WaitForServerResponse");
                    //PopupManager.HidePopups("LoadingPopup");

                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        PopupManager.HidePopups("LoadingPopup");
                        //Debug.log(dataObject.GetUtfString(ServerConstant.ERROR_CODE));
                        PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                    }
                    else
                    {
                        int roomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);

                        if (roomKey != -1)
                        {
                            if (SceneManager.CurrentScene.GetType() == typeof(MainMenuScene))
                            {
                                if (GameSmartFox != null)
                                {
                                    if (!GameSmartFox.IsConnected && !GameSmartFox.IsConnecting)
                                    {
                                        CapsaUtilities.CallDelayedMethod(2f, delegate()
                                        {
                                            SceneManager.LoadScene("RoomScene");
                                            CurrentRoomKey = roomKey;
                                            string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                                            GameHost = host;

                                            StartSFServer();
                                        });
                                    }
                                }
                                else
                                {
                                    CapsaUtilities.CallDelayedMethod(2f, delegate()
                                    {
                                        SceneManager.LoadScene("RoomScene");
                                        CurrentRoomKey = roomKey;
                                        string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                                        GameHost = host;

                                        StartSFServer();
                                    });
                                }
                            }
                            else
                            {
                                CurrentRoomKey = roomKey;
                                string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);
                                GameHost = host;

                                StartSFServer();
                            }
                        }
                        else
                        {
                            if (SceneManager.CurrentScene != null)
                            {
                                if (SceneManager.CurrentScene.GetType() == typeof(RoomScene))
                                    SceneManager.LoadScene("MainMenuScene");
                                else
                                    PopupManager.HidePopups("LoadingPopup");
                            }
                        }
                    }
                }
                break;
            case ServerConstant.INVITE_GET_USERS:
                {
                    //                    //Debug.log("INVITE GET USERS");

                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        string errorCode = dataObject.GetUtfString(ServerConstant.ERROR_CODE);
                        //Debug.log(errorCode);
                        PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                    }
                    else
                    {
                        int[] userList = dataObject.GetIntArray(ServerConstant.USER_LIST);
                        InvitePopup p = PopupManager.GetPopup("InvitePopup", false) as InvitePopup;

                        if (userList.Length > 0)
                            p.NoPlayerNotification.gameObject.SetActive(false);
                        else
                            p.NoPlayerNotification.gameObject.SetActive(true);

                        for (int i = 0; i < userList.Length; i++)
                        {
                            ISFSObject userData = dataObject.GetSFSObject(userList[i].ToString());
                            int userID = userData.GetInt(ServerConstant.USER_ID);
                            int gender = userData.GetInt(ServerConstant.GENDER);
                            int exp = userData.GetInt(ServerConstant.EXP);
                            int level = userData.GetInt(ServerConstant.LEVEL);
                            string nickname = userData.GetUtfString(ServerConstant.NICKNAME);

                            p.AddNewProfileObject(userID, gender, nickname, level);
                        }

                        p.LoadingIcon.SetActive(false);
                    }
                }
                break;
            case ServerConstant.SEND_COIN_TO_USER:
                {
                    if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                    {
                        List<String> errorMessage = new List<string>();
                        errorMessage.AddRange(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE));
                        SendServerError(dataObject.GetUtfString(ServerConstant.ERROR_CODE), errorMessage);

                        string message = errorMessage[(int)StringDataManager.ActiveLanguages];
                        PopupManager.ShowNotificationStorePopup(message);
                    }
                    else
                    {
                        long newCoin = dataObject.GetLong(ServerConstant.COINS);
                        string JsonString = "{\"InHandMoney\":" + newCoin.ToString() + "}";
                        OnCompleteSendGift(JsonString);
                    }
                }
                break;
            case ServerConstant.INVITE_TO_GAME:
                {
                    //                    //Debug.log("INVITE TO GAME");

                    if (PopupManager.isShowPopups("WebViewPopup") == true)
                        break;

                    if (PlayerPrefs.GetInt("BlockInvite", 1) == 1)
                    {
                        if (dataObject.ContainsKey(ServerConstant.ERROR_CODE))
                        {
                            string errorCode = dataObject.GetUtfString(ServerConstant.ERROR_CODE);
                            //Debug.log(errorCode);
                            PopupManager.ShowNotificationPopup(dataObject.GetUtfStringArray(ServerConstant.ERROR_MESSAGE)[0]);
                        }
                        else
                        {
                            if (SceneManager.CurrentScene.GetType() != typeof(RoomScene))
                            {
                                int userID = dataObject.GetInt(ServerConstant.USER_ID);
                                int gender = dataObject.GetInt(ServerConstant.GENDER);
                                int roomKey = dataObject.GetInt(ServerConstant.ROOM_KEY);
                                string nickname = dataObject.GetUtfString(ServerConstant.NICKNAME);
                                string host = dataObject.GetUtfString(ServerConstant.GAME_SERVER_HOST);

                                long baseBet = dataObject.GetLong(ServerConstant.BASE_BET);
                                bool hasMission = dataObject.GetBool(ServerConstant.HAS_MISSION);
                                bool winnerOnly = dataObject.GetBool(ServerConstant.WINNER_ONLY);
                                bool hiddenRoom = dataObject.GetBool(ServerConstant.IS_HIDDEN_ROOM);
                                bool isFriend = dataObject.GetBool(ServerConstant.IS_FRIEND);

                                CurrentRoomKey = roomKey;
                                GameHost = host;

                                ReceiveInvitationPopup p = PopupManager.GetPopup("ReceiveInvitationPopup", false) as ReceiveInvitationPopup;

                                bool ShowPopup = true;

                                if (p != null)
                                {
                                    if (p.gameObject.activeSelf)
                                    {
                                        ShowPopup = false;
                                    }
                                }

                                if (ShowPopup)
                                {
                                    PopupManager.ShowReceiveInvitationPopup(userID, gender, nickname, baseBet, winnerOnly, hasMission, hiddenRoom, isFriend);
                                }
                            }
                        }
                    }
                }
                break;
            case ServerConstant.DISPLAY_SOME_USERS:
                {

                    List<PlayerListData> PlayerOnlineData = new List<PlayerListData>();
					for (int i = 0; i < 12; i++)
                    {
                        ISFSObject data = dataObject.GetSFSObject(i.ToString());
                        int PlayerID = data.GetInt(ServerConstant.USER_ID);
                        bool isOnline = data.GetBool(ServerConstant.IS_ONLINE);
                        GenderType Gender = (GenderType)data.GetInt(ServerConstant.GENDER);

                        PlayerListData plData = new PlayerListData(PlayerID, isOnline, Gender);

                        PlayerOnlineData.Add(plData);

                    }

                    if (OnGetPlayerList != null)
                    {
                        OnGetPlayerList(JsonConvert.SerializeObject(PlayerOnlineData));
                    }
                }
                break;

            //			case ServerConstant.GET_FRIEND_REQUEST:
            //			{
            //				
            //				List<PlayerListData> PlayerOnlineData = new List<PlayerListData>();
            //				for(int i=0; i<12; i++)
            //				{
            //					ISFSObject data = dataObject.GetSFSObject(i.ToString());
            //					int PlayerID = data.GetInt(ServerConstant.USER_ID);
            //					bool isOnline = data.GetBool(ServerConstant.IS_ONLINE);
            //					//GenderType Gender = (GenderType)data.GetInt(ServerConstant.GENDER);
            //					
            //					PlayerListData plData = new PlayerListData(PlayerID, isOnline, GenderType.Male);
            //					
            //					PlayerOnlineData.Add(plData);
            //					
            //				}
            //				
            //				if(OnGetFriendRequest!=null)
            //				{
            //					OnGetFriendRequest(JsonConvert.SerializeObject(PlayerOnlineData));
            //				}
            //			}
            //			break;

            case ServerConstant.LIST_MY_FRIENDS:
                {
                    List<FriendStatusData> FriendStatusList = new List<FriendStatusData>();

                    List<FriendStatusData> offlineFriends = new List<FriendStatusData>();
                    for (int i = 0; i < dataObject.Size(); i++)
                    {
                        ISFSObject data = dataObject.GetSFSObject(i.ToString());

                        FriendStatusData plData = new FriendStatusData();

                        plData.PlayerID = data.GetInt(ServerConstant.USER_ID);
                        plData.nickname = data.GetUtfString(ServerConstant.NICKNAME);
                        plData.isOnline = data.GetBool(ServerConstant.IS_ONLINE);
                        plData.isInGame = data.GetBool(ServerConstant.IS_PLAYING_GAME);
                        //GenderType Gender = (GenderType)data.GetInt(ServerConstant.GENDER);

                        if (plData.isOnline)
                        {
                            FriendStatusList.Add(plData);
                        }
                        else
                        {
                            offlineFriends.Add(plData);
                        }
                    }

                    FriendStatusList.AddMany(offlineFriends);

                    if (OnGetFriendList != null)
                    {
                        OnGetFriendList(JsonConvert.SerializeObject(FriendStatusList));
                    }
                }
                break;

            case ServerConstant.GET_FRIEND_REQUEST:
                {
                    if (PopupManager.isShowPopups("WebViewPopup") == true)
                        break;

                    if (PlayerPrefs.GetInt("BlockRequest", 1) == 1)
                    {
                        PopupManager.ShowFriendRequestPopup(dataObject.GetInt(ServerConstant.USER_ID), true);
                    }
                }
                break;

            case ServerConstant.SEARCH_USER:
                {
                    FriendStatusData plData = new FriendStatusData();

                    plData.PlayerID = dataObject.GetInt(ServerConstant.USER_ID);
                    plData.isOnline = dataObject.GetBool(ServerConstant.IS_ONLINE);
                    plData.isInGame = dataObject.GetBool(ServerConstant.IS_PLAYING_GAME);

                    if (OnGetSearchFriendResult != null)
                    {
                        OnGetSearchFriendResult(JsonConvert.SerializeObject(plData));
                    }
                }
                break;

            case ServerConstant.APPROVED_FRIEND_REQUEST:
                {
                    if (PopupManager.isShowPopups("WebViewPopup") == true)
                        break;

                    PopupManager.ShowRequestAcceptedPopup(dataObject.GetInt(ServerConstant.USER_ID), dataObject.GetUtfString(ServerConstant.NICKNAME), (GenderType)dataObject.GetInt(ServerConstant.GENDER));
                }
                break;

            case ServerConstant.STOP_RECONNECT:
                {
                    _stopReconnect = true;
                }
                break;



        }


        //        //Debug.log("Command : " + cmd);
        //        //Debug.log("params : " + dataObject.GetDump(true));
    }

    #endregion

    #region WebServerUtility
    private IEnumerator initiateCountdown(string functionName, int timeInterval)
    {
       bool flag = false;
        //bool temp;
        if (serverResponseReceived.TryGetValue(functionName, out flag))
        {
            serverResponseReceived[functionName] = false;
        }
        else
        {
            serverResponseReceived.Add(functionName, false);
        }

        for (int i = 0; i < timeInterval * 10; i++)
        {
            yield return new WaitForSeconds(0.1f);
            if (serverResponseReceived[functionName])
            {
                break;
            }
        }

        if (!serverResponseReceived[functionName])
        {
            StopCoroutine(functionName);
            StopAllCoroutines();

            List<string> temp = new List<string>();
            temp.Add("Network connection is unstable, please try again later");

            SendServerError("E002", temp);

            m_instance._currentConnectionState = ConnectionState.Disconnected;
        }
        else
        {
            ////Debug.log("Server Response Received" + functionName);
        }
    }

    private IEnumerator initiateCountdown(string functionName)
    {
        yield return StartCoroutine(initiateCountdown(functionName, 20));
    }

    //	IEnumerator HandleRequests(){
    //		
    //		while(true){
    //			if(_currentConnectionState != ConnectionState.Disconnected){
    //				if(m_instance._requests.Count>0)
    //				{
    //					IEnumerator tempCountdown = m_instance.initiateCountdown("Request");
    //					
    //					StartCoroutine(tempCountdown);
    //					
    //					WWW WebServer = new WWW(m_instance._requests[0].QueuedServerLocation, m_instance._requests[0].QueuedForm);
    //					yield return WebServer;
    //					//Stop Countdown
    //					//serverResponseReceived["Register"] = true;
    //					StopCoroutine(tempCountdown);
    //					
    //					if (string.IsNullOrEmpty(WebServer.error))
    //					{
    //						if (WebServer.text.Contains("ErrorCode"))
    //						{
    //							ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
    //							SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
    //							
    //							m_instance._requests.Remove(m_instance._requests[0]);
    //							m_instance._currentConnectionState = ConnectionState.Connected;
    //						}
    //						else
    //						{
    //							////Debug.log(WebServer.text);
    //							string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
    //							//Debug.log(JsonString);
    //							
    //							m_instance._requests[0].ExecuteCallback(JsonString);
    //							
    //							m_instance._requests.Remove(m_instance._requests[0]);
    //							//HandleLogin(JsonString);
    //							
    //							m_instance._currentConnectionState = ConnectionState.Connected;
    //						}
    //					}
    //					else
    //					{
    //						//Debug.log("Error : "+WebServer.error);
    //						List<string> temp = new List<string>();
    //						temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
    //						//temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
    //						//temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
    //						//temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
    //						SendServerError("E001", temp);
    //					
    //						m_instance._currentConnectionState = ConnectionState.Disconnected;
    //					}
    //				}
    //			}
    //			
    //			yield return new WaitForSeconds(0.01f);
    //		}
    //		
    //	}

    //public void AfterLogin(string JsonString){
    //    JObject ObjectReceived = JObject.Parse(JsonString);
    //    PlayerID = (int)ObjectReceived["PlayerID"];
    //    Token = (string)ObjectReceived["Token"];
    //    string Location = (string)ObjectReceived["MasterServer"];
    //    PlayerPrefs.SetString("CapsaSaveData", Token);
    //    PlayerPrefs.Save();
    //    IsLogin = true;

    //    if (MServer != null)
    //    {
    //        if (MServer.IsConnected)
    //            MServer.Disconnect();
    //    }

    //    StartMasterServer(Location);
    //}

    //	public void Reconnect(){
    //		m_instance._currentConnectionState = ConnectionState.Reconnecting;
    //	}

    public void Reconnect()
    {
        //PopupManager.ShowPopup("LoadingTransactionPopup");
        StartCoroutine(ServerReconnect());
    }

    private IEnumerator ServerReconnect()
    {
        if ((cachedServerLocation != "") && (CachedForm != null))
        {
            //            //Debug.log("Reconnecting");
            //Initiate Countdown
            IEnumerator tempCountdown = initiateCountdown("ServerReconnect");
            StartCoroutine(tempCountdown);

            WWW WebServer = new WWW(cachedServerLocation, CachedForm);
            yield return WebServer;
            
            //Stop Countdown
            serverResponseReceived["ServerReconnect"] = true;
            StopCoroutine(tempCountdown);

            if (string.IsNullOrEmpty(WebServer.error))
            {
                if (WebServer.text.Contains("ErrorCode"))
                {

                    ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                    SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                    //JObject ObjectReceived = JObject.Parse(WebServer.text);
                    //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
                }
                else
                {
                    string JsonString;

                    /*if (IsLogin)
                        JsonString = DecryptAES(WebServer.text, TempToken.Substring(0, 44), TempToken.Substring(44, 24));
                    else*/
                        JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                    //Debug.log(JsonString);
                    ////Debug.log(CachedResponse.ToString());

                    if (CachedResponse != null)
                    {
                        CachedResponse(JsonString);
                    }

                    //Done, reset Reconnect
                    cachedServerLocation = "";
                    CachedForm = null;
                }
            }
            else
            {
                List<string> temp = new List<string>();
                temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
                SendServerError("E001", temp);
            }
        }



        PopupManager.HidePopups("LoadingTransactionPopup");
    }

    private IEnumerator Countdown(float timeout)
    {
        yield return new WaitForSeconds(timeout);
    }
    #endregion

    #region Authentication

    public void RegisterAsGuest()
    {
		Debug.Log("Test Register as Guest.");
        StartCoroutine(ServerRegisterAsGuest());

        IsLogin = false;
        //		string DeviceID = SystemInfo.deviceUniqueIdentifier;
        //		string LoginType = "Guest";
        //		string LoginPassword = SystemInfo.deviceUniqueIdentifier;
        //		TempToken = GenerateRandomKeys();
        //		
        //		GenderType guestGender = (GenderType)(UnityEngine.Random.Range(0,2));
        //		
        //		JObject SendObject = new JObject(new JProperty("Action", "RegisterUser"),
        //		                                 new JProperty("LoginType", LoginType),
        //		                                 new JProperty("LoginID", ""),
        //		                                 new JProperty("LoginPassword", LoginPassword),
        //		                                 new JProperty("ConfirmPassword", LoginPassword),
        //		                                 new JProperty("UserGender", guestGender),
        //		                                 new JProperty("DeviceID", DeviceID)
        //		                                 );
        //		
        //		WWWForm WebForm = new WWWForm();
        //		WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));
        //		
        //		m_instance._requests.Add(new QueuedRequest(WebForm, AuthServerLocation, OnServerLogin));
    }

    private IEnumerator ServerRegisterAsGuest()
    {
        //Debug.Log("AuthServerLocation : " + AuthServerLocation);
        IsLogin = false;
        string DeviceID = SystemInfo.deviceUniqueIdentifier;
        string LoginType = "Guest";
        string LoginPassword = SystemInfo.deviceUniqueIdentifier;
        TempToken = GenerateRandomKeys();

        GenderType guestGender = (GenderType)(UnityEngine.Random.Range(0, 2));

        JObject SendObject = new JObject(new JProperty("Action", "RegisterUser"),
                                         new JProperty("LoginType", LoginType),
                                         new JProperty("LoginOs", loginOS),
                                         new JProperty("GcmKey", gcmKey),
                                         new JProperty("ApnsKey", apnsKey),
                                         new JProperty("UserGender", guestGender),
                                         new JProperty("DeviceID", DeviceID),
                                         new JProperty("InternalCode", gameInternalCode)
                                         );

        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        ServerManager.Instance.HandleLogin = HandleOnServerRegister;
        CachedResponse = HandleLogin;

        //Initiate Countdown
        IEnumerator tempCountdown = initiateCountdown("Register");
        StartCoroutine(tempCountdown);
        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;
        //Stop Countdown
        serverResponseReceived["Register"] = true;
        StopCoroutine(tempCountdown);

        //m_instance._requests.Add(new QueuedRequest(WebForm, AuthServerLocation, OnServerLogin);

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
                //Debug.log(JsonString);

                HandleLogin(JsonString);
                PlayerPrefs.SetInt("AccountType", (int)AccountType.GameServer);
                PlayerPrefs.Save();
            }
        }
        else
        {
            //            //Debug.log("Error : " + WebServer.error);
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void HandleOnServerRegister(string JsonData)
    {
        //Debug.Log("HandleOnServerRegister");

        JObject ObjectReceived = JObject.Parse(JsonData);
        PlayerID = (int)ObjectReceived["PlayerID"];
        Token = (string)ObjectReceived["Token"];
		int Gender = (int)ObjectReceived["Gender"];
        MasterLocation = (string)ObjectReceived["MasterServer"];
		//Debug.Log ("ServerRegister : " + MasterLocation);
        PlayerPrefs.SetString("CapsaSaveData", Token);
        PlayerPrefs.Save();
        IsLogin = true;

        string ip = (string)ObjectReceived["WebServer"];

		//Setting UserData
		UserData.checkLogin = true;
		UserData.userPidx = PlayerID;
		UserData.userToken = Token;
		UserData.ServerLocation = MasterLocation;
		UserData.userGender	= Gender;

        if (ip != null)
        {
            this.SetupWebServerAdress(ip);
        }

        if (MServer != null)
        {
            if (MServer.IsConnected)
                MServer.Disconnect();
        }

        StartMasterServer(MasterLocation);

        if (OnServerLogin != null)
        {
            this.OnServerLogin(JsonData);
        }
    }

    public void LoginAsGuest()
    {

    }

    public void RegisterToServer(string ID, string Password, GenderType userGender, string accountEmail)
    {
#if UNITY_STANDALONE
        StartCoroutine(Register(ID, Password, userGender, accountEmail, "-"));
#else
        StartCoroutine(Register(ID, Password, userGender, accountEmail, PlayerPrefs.GetString("CapsaSaveData", "")));
#endif
    }

    private IEnumerator Register(string ID, string Password, GenderType userGender, string accountEmail, string GuestToken)
    {

        //yield return (new WaitForSeconds(1.0f));
        IsLogin = false;
        string DeviceID = SystemInfo.deviceUniqueIdentifier;
        string LoginType = "Email";
        string LoginPassword = Password;

        JObject SendObject = new JObject(new JProperty("Action", "RegisterUser"),
                                         new JProperty("LoginType", LoginType),
                                         new JProperty("LoginID", ID),
                                         new JProperty("LoginPassword", LoginPassword),
                                         new JProperty("LoginOs", loginOS),
                                         new JProperty("GcmKey", gcmKey),
                                         new JProperty("ApnsKey", apnsKey),
                                         new JProperty("UserGender", userGender),
                                         new JProperty("GuestToken", GuestToken),
                                         new JProperty("AccountEmail", accountEmail),
                                         new JProperty("DeviceID", DeviceID)
                                         );
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        ServerManager.Instance.HandleLogin = HandleOnServerRegister;
        CachedResponse = HandleLogin;

        //Initiate Countdown
        IEnumerator tempCountdown = initiateCountdown("Register");
        StartCoroutine(tempCountdown);
        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;
        //Stop Countdown
        serverResponseReceived["Register"] = true;
        StopCoroutine(tempCountdown);

        if (string.IsNullOrEmpty(WebServer.error))
        {

            ////Debug.log(WebServer.text);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
                HandleLogin(JsonString);

                PlayerPrefs.SetInt("AccountType", (int)AccountType.GameServer);
                PlayerPrefs.Save();
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void HandleOnServerLogin(string JsonData)
    {
        JObject ObjectReceived = JObject.Parse(JsonData);
        PlayerID = (int)ObjectReceived["PlayerID"];
        Token = (string)ObjectReceived["Token"];
		int Gender = (int)ObjectReceived["Gender"];
		//MasterLocation = "128.199.81.224";
        MasterLocation = (string)ObjectReceived["MasterServer"];
		//Debug.Log (MasterLocation);
        PlayerPrefs.SetString("CapsaSaveData", Token);
        PlayerPrefs.Save();
        IsLogin = true;

        string ip = (string)ObjectReceived["WebServer"];

		//Setting UserData
		UserData.checkLogin = true;
		UserData.userPidx = PlayerID;
		UserData.userToken = Token;
		UserData.ServerLocation = MasterLocation;
		UserData.userGender	= Gender;


        if (ip != null)
        {
            this.SetupWebServerAdress(ip);
        }

        if (MServer != null)
        {
            if (MServer.IsConnected)
                MServer.Disconnect();
        }

        StartMasterServer(MasterLocation);

        //yield return StartCoroutine(TryDownloadUserPhoto());

        //HandleLogin(JsonString);
        if (Instance.OnServerLogin != null)
        {
            Instance.OnServerLogin(JsonData);
        }
    }

    public void LoginToServer()
    {
		Token = PlayerPrefs.GetString("CapsaSaveData", string.Empty);
        StartCoroutine(LoginWithAuth());
    }

    public void LoginToServerWithFacebook(string id, string firstName, string lastName, string accessToken, string businessToken, GenderType userGender)
    {
        StartCoroutine(LoginWithFacebookAuth(id, firstName, lastName, accessToken, businessToken, userGender));
    }

    public void LoginToServerWithGoogle(string id, string first_name, string last_name, string accessToken, GenderType userGender, string imageURL)
    {
        StartCoroutine(LoginWithGoogleAuth(id, first_name, last_name, accessToken, userGender, imageURL));
    }

    private IEnumerator LoginWithAuth()
    {
        IsLogin = false;
        JObject SendObject = new JObject(new JProperty("Action", "LoginUserWithAuthToken"),
                                         new JProperty("Token", Token),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier),
                                         new JProperty("InternalCode", gameInternalCode)
                                         );
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;

        //Initiate Countdown
        IEnumerator tempCountdown = initiateCountdown("Login");
        StartCoroutine(tempCountdown);
        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;
        //Stop Countdown
        serverResponseReceived["Login"] = true;
        StopCoroutine(tempCountdown);

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                Debug.Log(WebServer.text);
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
                HandleLogin(JsonString);

                PlayerPrefs.SetInt("AccountType", (int)AccountType.GameServer);
                PlayerPrefs.Save();

                if (OnSignInSuccess != null)
                    OnSignInSuccess();
            }
        }
        else
        {
            //Debug.log(WebServer.error);
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }


    private IEnumerator LoginWithFacebookAuth(string ID, string firstName, string lastName, string accessToken, string tokenForBusiness, GenderType userGender)
    {

        IsLogin = false;

        if (userGender == GenderType.Other)
        {
            // PopUp Gender Select,but Temporary Default Male
            userGender = GenderType.Male;
        }
        //
        string DeviceID = SystemInfo.deviceUniqueIdentifier;
        string GuestToken = PlayerPrefs.GetString("CapsaSaveData", "");
        JObject SendObject = new JObject(new JProperty("Action", "LoginUserWithFacebook"),
                                         new JProperty("FacebookID", ID),
                                         new JProperty("FacebookClientToken", accessToken),
                                         new JProperty("FacebookBusinessToken", tokenForBusiness),
                                         new JProperty("UserGender", userGender),
                                         new JProperty("FirstName", firstName),
                                         new JProperty("LastName", lastName),
                                         new JProperty("DeviceID", DeviceID),
                                         new JProperty("LoginOs", loginOS),
                                         new JProperty("GcmKey", gcmKey),
                                         new JProperty("ApnsKey", apnsKey),
                                         new JProperty("GuestToken", GuestToken),
                                         new JProperty("InternalCode", gameInternalCode)
                                         );

        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

		//Debug.Log (EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        CachedResponse = HandleLogin;
        //cachedSelectedServer = SelectedServer;

        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                Debug.Log(WebServer.text);
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                HandleLogin(JsonString);

                JObject jobj = JObject.Parse(JsonString);
                bool isRegistered = (bool)jobj["isRegister"];

                if (isRegistered)
                    FacebookManager.instance.LoadProfileImage(ID, GetFacebookProfilePicture);
             
                PlayerPrefs.SetInt("AccountType", (int)AccountType.Facebook);
                PlayerPrefs.Save();

                if (OnSignInSuccess != null)
                    OnSignInSuccess();
            }
        }
        else
        {
            //Debug.log(WebServer.error);
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    private IEnumerator LoginWithGoogleAuth(string id,
                                            string first_name,
                                            string last_name,
                                            string accessToken,
                                            GenderType userGender,
                                            string imageURL)
    {
        IsLogin = false;

        if (userGender == GenderType.Other)
        {
            // PopUp Gender Select,but Temporary Default Male
            userGender = GenderType.Male;
        }

        string DeviceID = SystemInfo.deviceUniqueIdentifier;
        string GuestToken = PlayerPrefs.GetString("CapsaSaveData", "");

        JObject SendObject = new JObject(new JProperty("Action", "LoginUserWithGoogle"),
                                         new JProperty("LoginType", "Google"),
                                         new JProperty("GoogleID", id),
                                         new JProperty("GoogleToken", accessToken),
                                         new JProperty("UserGender", userGender),
                                         new JProperty("FirstName", first_name),
                                         new JProperty("LastName", last_name),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier),
                                         new JProperty("LoginOs", loginOS),
                                         new JProperty("GcmKey", gcmKey),
                                         new JProperty("ApnsKey", apnsKey),
                                         new JProperty("GuestToken", GuestToken),
                                         new JProperty("InternalCode", gameInternalCode)
                                         );
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        //        //Debug.log("============= Sending Login Info with Google ===============");
        //Debug.log(SendObject.ToString());

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        HandleLogin = HandleOnServerLogin;
        CachedResponse = HandleLogin;
        //cachedSelectedServer = SelectedServer;

        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                //Debug.log(WebServer.text);
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                HandleLogin(JsonString);

                JObject jobj = JObject.Parse(JsonString);
                bool isRegistered = (bool)jobj["isRegister"];

                if (isRegistered)
                {
                    GoogleAccountManager.instance.LoadProfilePicture(GoogleAccountManager.instance.googleAccountInfo.image.url, GetGooglePlayProfilePicture);
                }

                PlayerPrefs.SetInt("AccountType", (int)AccountType.GoogleAccount);

                //Debug.log("Connected Google Account Email : " + GooglePlayManager.instance.currentAccount);
                PlayerPrefs.SetString("GoogleEmail", GooglePlayManager.instance.currentAccount);

                PlayerPrefs.Save();

                if (OnSignInSuccess != null)
                    OnSignInSuccess();
            }
        }
        else
        {
            //Debug.log(WebServer.error);
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void LoginToServer(string loginID, string password, string loginType)
    {
        StartCoroutine(LoginWithType(loginID, password, loginType));
    }

    private IEnumerator LoginWithType(string loginID, string password, string loginType)
    {
        IsLogin = false;

        string GuestToken = PlayerPrefs.GetString("CapsaSaveData", "");
        JObject SendObject = new JObject(new JProperty("Action", "LoginUserWithEmail"),
                                         new JProperty("LoginID", loginID),
                                         new JProperty("LoginPassword", password),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier),
                                         new JProperty("LoginOs", loginOS),
                                         new JProperty("GcmKey", gcmKey),
                                         new JProperty("ApnsKey", apnsKey),
                                         new JProperty("GuestToken", GuestToken),
                                         new JProperty("InternalCode", gameInternalCode)
                                         );


		Debug.Log (SendObject.ToString());
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        HandleLogin = HandleOnServerLogin;
        CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        //Initiate Countdown
        IEnumerator tempCountdown = initiateCountdown("Login");
        StartCoroutine(tempCountdown);
        WWW WebServer = new WWW(AuthServerLocation, WebForm);
        yield return WebServer;

        //Stop Countdown
        serverResponseReceived["Login"] = true;
        StopCoroutine(tempCountdown);

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

				Debug.Log (JsonString);
                HandleLogin(JsonString);

                PlayerPrefs.SetInt("AccountType", (int)AccountType.GameServer);
                PlayerPrefs.Save();

            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }
    #endregion

    #region GameData
    public void ClaimBankrupt()
    {
        StartCoroutine(ServerClaimBankrupt());
    }

    private IEnumerator ServerClaimBankrupt()
    {
        JObject SendObject = new JObject(new JProperty("Action", "ClaimBankrupt"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //        cachedServerLocation = AuthServerLocation;
        //        CachedResponse = ;
        //		

        //		IEnumerator tempCountdown = initiateCountdown("Login");
        //		StartCoroutine(tempCountdown);

        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        //Stop Countdown
        //		serverResponseReceived["Login"] = true;
        //		StopCoroutine(tempCountdown);

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);
                PlayerData = JsonConvert.DeserializeObject<UserProfileData>(JsonString);
                //PopupManager.HidePopups("FreeChargePopup");
                SpecialEvetButtonList.specialEventData.BankruptFreeCharge = false;

                GetUserData();
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetUserData()
    {
        StartCoroutine(ServerGetUserData());
    }

    private IEnumerator ServerGetUserData()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetUserData"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        CachedResponse = OnGetUserData;
        //		cachedSelectedServer = SelectedServer;
        //	

        IEnumerator tempCountdown = initiateCountdown("GetUser");
        StartCoroutine(tempCountdown);

        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        //Stop Countdown
        serverResponseReceived["GetUser"] = true;
        StopCoroutine(tempCountdown);

        //PopupManager.HidePopups("LoadingPopup");
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                //                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                PlayerData = JsonConvert.DeserializeObject<UserProfileData>(JsonString);

                PlayerPrefs.SetInt("CapsaGirl", (int)PlayerData.ActiveGirl);

                if (OnGetUserData != null)
                {
                    OnGetUserData(JsonString);
                }

                yield return StartCoroutine(TryDownloadUserPhoto());
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetPlayerData(int PlayerID)
    {
        StartCoroutine(ServerGetPlayerData(PlayerID));
    }

    private IEnumerator ServerGetPlayerData(int PlayerID)
    {
        long startTime = DateTime.Now.Ticks;

        JObject SendObject = new JObject(new JProperty("Action", "GetPlayerData"),
                                         new JProperty("PlayerID", PlayerID),
                                         new JProperty("UserID", this.PlayerData.PlayerID),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        cachedServerLocation = AuthServerLocation;
        CachedResponse = OnGetPlayerData;

        IEnumerator tempCountdown = initiateCountdown("GetPlayer");
        StartCoroutine(tempCountdown);

        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        long endTime = DateTime.Now.Ticks;

        //        //Debug.log("Elapsed time to connect to server and get response : " + (new TimeSpan(endTime - startTime)).TotalSeconds + " second(s)");
        testMessage = "Elapsed time to connect to server and get response : " + (new TimeSpan(endTime - startTime)).TotalSeconds + " second(s)\n";

        //Stop Countdown
        serverResponseReceived["GetPlayer"] = true;
        StopCoroutine(tempCountdown);


        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                //Debug.log("Player ID : " + PlayerID.ToString());
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                //yield return StartCoroutine(TryDownloadUserPhoto());

                if (OnGetPlayerData != null)
                {
                    OnGetPlayerData(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }


    }

    public void ChangeNickname(string newNickname)
    {
        StartCoroutine(ServerChangeNickname(newNickname));
    }

    public IEnumerator ServerChangeNickname(string newNickname)
    {
        JObject SendObject = new JObject(new JProperty("Action", "ChangeUserNickname"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier),
                                         new JProperty("NewNickname", newNickname));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                /*if (OnGetUserData != null)
                {
                    OnGetUserData(JsonString);
                }*/

                if (OnProfileNickEdit != null)
                    OnProfileNickEdit(JsonString);
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ChangePersonalMessage(string newPersonalMessage)
    {
        StartCoroutine(ServerChangePersonalMessage(newPersonalMessage));
    }

    public IEnumerator ServerChangePersonalMessage(string newPersonalMessage)
    {
        JObject SendObject = new JObject(new JProperty("Action", "ChangeUserPersonalMessage"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier),
                                         new JProperty("NewPersonalMessage", newPersonalMessage));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                /*if (OnGetUserData != null)
                {
                    OnGetUserData(JsonString);
                }*/

                if (OnProfilePerEdit != null)
                    OnProfilePerEdit(JsonString);
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetFacebookProfilePicture(string url, Texture2D texture)
    {
        if (texture == null)
        {
            if (currentRetryCountGetProfilePicture < MAX_RETRY_COUNT)
            {
                FacebookManager.instance.LoadPicture(url, GetFacebookProfilePicture);
                currentRetryCountGetProfilePicture++;
            }
            else
            {
                currentRetryCountGetProfilePicture = 0;
            }

            return;
        }

        PlayerPhoto = texture;

        if (OnProfilePictureUpdate != null)
            OnProfilePictureUpdate("");

        UploadProfilePicture(PlayerPhoto);
    }

    public void GetGooglePlayProfilePicture(string url, Texture2D texture)
    {
        if (texture == null)
        {
            if (currentRetryCountGetProfilePicture < MAX_RETRY_COUNT)
            {
                GoogleAccountManager.instance.LoadProfilePicture(url, GetGooglePlayProfilePicture);
                currentRetryCountGetProfilePicture++;
            }
            else
            {
                currentRetryCountGetProfilePicture = 0;
            }

            return;
        }

        PlayerPhoto = texture;

        if (OnProfilePictureUpdate != null)
            OnProfilePictureUpdate("");

        UploadProfilePicture(PlayerPhoto);
    }

    public void UploadProfilePicture(Texture2D newImage)
    {
        StartCoroutine(ServerUploadProfilePicture(newImage));
    }

    public IEnumerator ServerUploadProfilePicture(Texture2D newImage)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "ChangeProfilePicture"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));

        WWWForm WebForm = new WWWForm();
        // version 1
        //postForm.AddBinaryData("theFile",localFile.bytes);

        // version 2
        WebForm.AddBinaryData("ProfilePicture", newImage.EncodeToJPG(), PlayerID.ToString());
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        WWW WebServer = new WWW(ImageServerLocation, WebForm);
        yield return WebServer;

        PopupManager.HidePopups("LoadingTransactionPopup");
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);
                PlayerPhoto = newImage;

                //Do something
                if (OnChangeProfilePicture != null)
                {
                    OnChangeProfilePicture(JsonString);
                }
            }

        }
        else
        {
            //Debug.log("Error during upload: " + WebServer.error);
            PopupManager.ShowNotificationPopup("Error " + WebServer.error);
        }
    }

    private IEnumerator TryDownloadUserPhoto()
    {
        string photoURL = ImageURL + PlayerID.ToString() + ".jpg";
        WWW WebServer = new WWW(photoURL);

        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.texture != null)
            {
                PlayerPhoto = new Texture2D(1, 1);
                PlayerPhoto.LoadImage(WebServer.texture.EncodeToJPG());
            }
            else
            {
                if (PlayerData.Gender == GenderType.Male)
                    PlayerPhoto = Resources.Load<Texture2D>("Images/DefaultMale");
                else
                    PlayerPhoto = Resources.Load<Texture2D>("Images/DefaultFemale");
            }
        }
        else
        {
            if (PlayerData.Gender == GenderType.Male)
                PlayerPhoto = Resources.Load<Texture2D>("Images/DefaultMale");
            else
                PlayerPhoto = Resources.Load<Texture2D>("Images/DefaultFemale");
        }

        if (OnProfilePictureUpdate != null)
            OnProfilePictureUpdate("");
    }

    public void GetUserFriendRequest()
    {
        StartCoroutine(ServerGetUserFriendRequest());
    }

    IEnumerator ServerGetUserFriendRequest()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetFriendRequestList"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                yield return StartCoroutine(TryDownloadUserPhoto());

                if (OnGetFriendRequestList != null)
                {
                    OnGetFriendRequestList(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }


    public void GetUserProperty()
    {
        StartCoroutine(ServerGetUserProperty());
    }

    private IEnumerator ServerGetUserProperty()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetUserPropertyData"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("DeviceID", SystemInfo.deviceUniqueIdentifier));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                yield return StartCoroutine(TryDownloadUserPhoto());

                if (OnGetUserData != null)
                {
                    OnGetUserData(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetPlayerPropertyData(int playerID)
    {
        StartCoroutine(ServerGetPlayerPropertyData(playerID));
    }

    private IEnumerator ServerGetPlayerPropertyData(int playerID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetPlayerPropertyData"),
                                         new JProperty("PlayerID", playerID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);
                if (OnGetPlayerProperty != null)
                {
                    OnGetPlayerProperty(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetUserPropertyList()
    {
        StartCoroutine(ServerGetUserPropertyList());
    }

    private IEnumerator ServerGetUserPropertyList()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetUserPropertyList"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnGetUserPropertyList != null)
                {
                    OnGetUserPropertyList(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void BuyProperty(int propertyID, List<int> targetIDs)
    {
        StartCoroutine(ServerBuyPropertyUseCoins(propertyID, targetIDs));
    }

    public IEnumerator ServerBuyPropertyUseCoins(int propertyID, List<int> targetIDs)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "BuyPropertyUseCoin"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("PropertyID", propertyID),
                                         new JProperty("TargetIDs", JsonConvert.SerializeObject(targetIDs)));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            PopupManager.HidePopups("LoadingTransactionPopup");

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                JObject ObjectReceived = JObject.Parse(JsonString);
                long money = (long)ObjectReceived["InHandMoney"];
                string message = (string)ObjectReceived["Message"];

                PopupManager.ShowFadeMessagePopup(message, new Color(124f / 255f, 239f / 255f, 1.0f));
                PlayerData.InHandMoney = money;
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void BuyPropertyUseDiamond(int propertyID, List<int> targetIDs)
    {
        StartCoroutine(ServerBuyPropertyUseDiamond(propertyID, targetIDs));
    }

    public IEnumerator ServerBuyPropertyUseDiamond(int propertyID, List<int> targetIDs)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "BuyPropertyUseDiamond"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("PropertyID", propertyID),
                                         new JProperty("TargetIDs", JsonConvert.SerializeObject(targetIDs)));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		

        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;

        if (string.IsNullOrEmpty(WebServer.error))
        {
            PopupManager.HidePopups("LoadingTransactionPopup");
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);

                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                //JObject ObjectReceived = JObject.Parse(JsonString);
                //string recipeID = (string)ObjectReceived["RecipeID"];

                //ClaimPurchaseRecipe(recipeID);

                JObject ObjectReceived = JObject.Parse(JsonString);
                long money = (long)ObjectReceived["InHandMoney"];
                string message = (string)ObjectReceived["Message"];

                PopupManager.ShowFadeMessagePopup(message, new Color(124f / 255f, 239f / 255f, 1.0f));
                PlayerData.InHandMoney = money;
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ClaimPurchaseRecipe(string recipeID)
    {
        StartCoroutine(ServerClaimPurchaseRecipe(recipeID));
    }

    public IEnumerator ServerClaimPurchaseRecipe(string recipeID)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "ClaimPurchaseRecipe"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("RecipeID", recipeID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            PopupManager.HidePopups("LoadingTransactionPopup");
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                JObject ObjectReceived = JObject.Parse(JsonString);
                long money = (long)ObjectReceived["InHandMoney"];
                string message = (string)ObjectReceived["Message"];

                if (ObjectReceived["GirlID"] != null)
                {
                    PlayerPrefs.SetInt("CapsaGirl", (int)ObjectReceived["GirlID"]);
                }

                PopupManager.ShowFadeMessagePopup(message, new Color(124f / 255f, 239f / 255f, 1.0f));
                PlayerData.InHandMoney = money;
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void BuyCapsaGirlUseDiamond(int girlID)
    {
        StartCoroutine(ServerBuyCapsaGirlUseDiamond(girlID));
    }

    public IEnumerator ServerBuyCapsaGirlUseDiamond(int girlID)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "BuyCapsaGirlUseDiamond"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("GirlID", girlID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            PopupManager.HidePopups("LoadingTransactionPopup");
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                //JObject ObjectReceived = JObject.Parse(JsonString);
                //string recipeID = (string)ObjectReceived["RecipeID"];

                //ClaimPurchaseRecipe(recipeID);

                JObject ObjectReceived = JObject.Parse(JsonString);
                long money = (long)ObjectReceived["InHandMoney"];
                string message = (string)ObjectReceived["Message"];

                if (ObjectReceived["GirlID"] != null)
                {
                    PlayerPrefs.SetInt("CapsaGirl", (int)ObjectReceived["GirlID"]);
                }

                PopupManager.ShowFadeMessagePopup(message, new Color(124f / 255f, 239f / 255f, 1.0f));
                PlayerData.InHandMoney = money;
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void BuyCoinUseDiamond(string storeID)
    {
        StartCoroutine(ServerBuyCoinUseDiamond(storeID));
    }

    public IEnumerator ServerBuyCoinUseDiamond(string storeID)
    {
        PopupManager.ShowPopup("LoadingTransactionPopup");
        JObject SendObject = new JObject(new JProperty("Action", "BuyCoinUseDiamond"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("StoreID", storeID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            PopupManager.HidePopups("LoadingTransactionPopup");
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                //JObject ObjectReceived = JObject.Parse(JsonString);
                //string recipeID = (string)ObjectReceived["RecipeID"];

                //ClaimPurchaseRecipe(recipeID);

                JObject ObjectReceived = JObject.Parse(JsonString);
                long money = (long)ObjectReceived["InHandMoney"];
                string message = (string)ObjectReceived["Message"];

                PopupManager.ShowFadeMessagePopup(message, new Color(124f / 255f, 239f / 255f, 1.0f));
                PlayerData.InHandMoney = money;
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ListAllMyFriends()
    {
        StartCoroutine(ServerListAllMyFriends());
    }

    public IEnumerator ServerListAllMyFriends()
    {
        JObject SendObject = new JObject(new JProperty("Action", "ListAllMyFriends"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("GiftToFriendPopup", false) as GiftToFriendPopup).LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnListAllMyFriends != null)
                {
                    OnListAllMyFriends(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public int totalInvite = 0;

    public void RequestTotalInvite()
    {
        StartCoroutine(ServerTotalInvite());
    }

    public IEnumerator ServerTotalInvite()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetTotalInvite"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {


            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);
                JObject ObjectReceived = JObject.Parse(JsonString);

                totalInvite = (int)ObjectReceived["TotalReferral"];
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    string Encrypt(string data)
    {
        return EncryptAES(data, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
    }

    string Decrypt(string rawData)
    {
        return DecryptAES(rawData, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
    }

    public void CheckClientVersion()
    {
        StartCoroutine(ServerCheckClientVersion());
    }

    IEnumerator ServerCheckClientVersion()
    {
        JObject jsonData = new JObject(new JProperty("Action", "GetVersion"),
                                       new JProperty("ClientVersion", clientVersion.ToString()),
                                       new JProperty("Platform", PLATFORM),
                                       new JProperty("InternalCode", gameInternalCode));

		WWWForm form = new WWWForm();
        form.AddField("Data", Encrypt(jsonData.ToString()));

        CachedForm = form;
        CachedResponse = OnCheckClientVersion;
        cachedServerLocation = AuthServerLocation;
        IEnumerator tempCountdown = initiateCountdown("Version");
        StartCoroutine(tempCountdown);
        
        WWW www = new WWW(AuthServerLocation, form);

        yield return www;

        if (string.IsNullOrEmpty(www.error))
        {
            if (www.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(www.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);

                string errorMessage =
                    ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(errorMessage);
            }
            else
            {
				serverResponseReceived["Version"] = true;
                StopCoroutine(tempCountdown);

				if (OnCheckClientVersion != null)
                {
                    OnCheckClientVersion(Decrypt(www.text));
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            SendServerError("E001", temp);
        }
    }

    public void UseVoucher(string[] keys)
    {
        if (keys == null ||
            keys.Length < 5)
        {
            return;
        }

        StartCoroutine(SendVoucher(keys));
    }

    IEnumerator SendVoucher(string[] keys)
    {
        JObject jsonData = new JObject(new JProperty("Action", "TopUpDiamondUseVoucher"),
                                       new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                       new JProperty("Code1", keys[0]),
                                       new JProperty("Code2", keys[1]),
                                       new JProperty("Code3", keys[2]),
                                       new JProperty("Code4", keys[3]),
                                       new JProperty("Code5", keys[4]));
        WWWForm form = new WWWForm();
        form.AddField("Data", Encrypt(jsonData.ToString()));

        CachedForm = form;

        WWW www = new WWW(GameDataServerLocation, form);

        yield return www;

        if (string.IsNullOrEmpty(www.error))
        {
            if (www.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(www.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);

                string errorMessage =
                    ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(errorMessage);

                if (OnUseVoucherFailed != null)
                {
                    OnUseVoucherFailed();
                }
            }
            else
            {
                if (OnUseVoucherSuccess != null)
                {
                    OnUseVoucherSuccess(Decrypt(www.text));
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            SendServerError("E001", temp);

            if (OnUseVoucherFailed != null)
            {
                OnUseVoucherFailed();
            }
        }
    }

    public void RequestApplyGooglePurchase(BillingResult result)
    {
        string price = "";
        if (DiamondDataManager.data.ContainsKey(result.purchase.SKU))
        {
            price = DiamondDataManager.data[result.purchase.SKU].price.ToString();
        }
        StartCoroutine(RequestServerApplyGooglePurchase(result, price));
    }

    private IEnumerator RequestServerApplyGooglePurchase(BillingResult result, string price)
    {
        JObject SendObject = new JObject(new JProperty("Action", "BuyDiamondUseStore"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("StoreType", "android"),
                                         new JProperty("Data1", result.purchase.originalJson),
                                         new JProperty("Data2", result.purchase.signature),
                                         new JProperty("InternalCode", gameInternalCode));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        //Debug.Log("Token + deviceID : " + Token + SystemInfo.deviceUniqueIdentifier);
        //Debug.Log("developerPayload : " + result.purchase.developerPayload);

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);



        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));

                if (OnGoogleInAppFailedConsume != null)
                {
                    OnGoogleInAppFailedConsume(result);
                }
            }
            else
            {
                //Debug.Log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
                //Debug.Log(JsonString);
                if (OnGoogleInAppSuccessConsume != null)
                {
                    OnGoogleInAppSuccessConsume(result);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void SellProperty(int propertyID)
    {
        StartCoroutine(ServerSellProperty(propertyID));
    }

    public IEnumerator ServerSellProperty(int propertyID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "SellProperty"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("PropertyID", propertyID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);
                if (OnSellProperty != null)
                {
                    OnSellProperty(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void SendGiftToFriend(int targetID, GiftType giftType, long value)
    {
        StartCoroutine(ServerSendGiftToFriend(targetID, giftType, value));
    }

    public IEnumerator ServerSendGiftToFriend(int targetID, GiftType giftType, long value)
    {
        JObject SendObject = new JObject(new JProperty("Action", "SendGiftToFriend"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetID", targetID),
                                         new JProperty("GiftType", giftType),
                                         new JProperty("Value", value));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnCompleteSendGift != null)
                {
                    OnCompleteSendGift(JsonString);
                }

            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ListMyGift()
    {
        StartCoroutine(ServerListMyGift());
    }

    public IEnumerator ServerListMyGift()
    {
        JObject SendObject = new JObject(new JProperty("Action", "ListMyGift"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnListMyGift != null)
                {
                    OnListMyGift(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void AcceptGift(List<int> giftIDs)
    {
        StartCoroutine(ServerAcceptGift(giftIDs));
    }

    public IEnumerator ServerAcceptGift(List<int> giftIDs)
    {
        JObject SendObject = new JObject(new JProperty("Action", "AcceptGift"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("GiftIDs", JsonConvert.SerializeObject(giftIDs)));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnAcceptGift != null)
                {
                    OnAcceptGift(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void RequestSpecialEventNumbers()
    {
        StartCoroutine(GetSpecialEventNumbers());
    }

    public IEnumerator GetSpecialEventNumbers()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetSpecialEventNumbers"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                //                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnGetSpecialEventNumbers != null)
                {
                    OnGetSpecialEventNumbers(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void RequestDailySpinResult()
    {
        StartCoroutine(UseDailySpin());
    }

    public IEnumerator UseDailySpin()
    {
        JObject SendObject = new JObject(new JProperty("Action", "UseDailySpin"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
                if (OnGetUseDailySpinResultFailed != null)
                    OnGetUseDailySpinResult(null);
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnGetUseDailySpinResult != null)
                {
                    OnGetUseDailySpinResult(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void GetMyGirlList()
    {
        StartCoroutine(ServerGetMyGirlList());
    }

    public IEnumerator ServerGetMyGirlList()
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetMyGirlList"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnGetMyGirlList != null)
                {
                    OnGetMyGirlList(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ChangeActiveGirl(int girlID)
    {
        StartCoroutine(ServerChangeActiveGirl(girlID));
    }

    public IEnumerator ServerChangeActiveGirl(int girlID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "ChangeActiveGirl"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("GirlID", girlID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnChangeActiveGirl != null)
                {
                    OnChangeActiveGirl(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void LoadBlockList()
    {
        StartCoroutine(ServerLoadBlockList());
    }

    public IEnumerator ServerLoadBlockList()
    {
        JObject SendObject = new JObject(new JProperty("Action", "LoadBlockList"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("BlacklistPopup", false) as BlacklistPopup).LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnLoadBlockList != null)
                {
                    OnLoadBlockList(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void BlockPlayer(int targetID)
    {
        StartCoroutine(ServerBlockPlayer(targetID));
    }

    public IEnumerator ServerBlockPlayer(int targetID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "BlockPlayer"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetID", targetID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("InboxPopup", false) as InboxPopup).LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnBlockPlayer != null)
                {
                    OnBlockPlayer(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void UnblockPlayers(List<int> targetIDs)
    {
        StartCoroutine(ServerUnblockPlayers(targetIDs));
    }

    public IEnumerator ServerUnblockPlayers(List<int> targetIDs)
    {
        JObject SendObject = new JObject(new JProperty("Action", "UnblockPlayers"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetIDs", JsonConvert.SerializeObject(targetIDs)));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            InboxPopup inbox = PopupManager.GetPopup("InboxPopup", false) as InboxPopup;
            if (inbox != null)
                inbox.LoadingCircle.SetActive(false);

            BlacklistPopup blacklist = PopupManager.GetPopup("BlacklistPopup", false) as BlacklistPopup;
            if (blacklist != null)
                blacklist.LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnUnblockPlayer != null)
                {
                    OnUnblockPlayer(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void LoadAllMessages()
    {
        StartCoroutine(ServerLoadAllMessages());
    }

    public IEnumerator ServerLoadAllMessages()
    {
        JObject SendObject = new JObject(new JProperty("Action", "LoadAllMessages"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("MessagePopup", false) as MessagePopup).LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnLoadAllMessages != null)
                {
                    OnLoadAllMessages(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ComposeMessageByNickname(string targetNickname, string targetMessage)
    {
        StartCoroutine(ServerComposeMessageByNickname(targetNickname, targetMessage));
    }

    public IEnumerator ServerComposeMessageByNickname(string targetNickname, string targetMessage)
    {
        JObject SendObject = new JObject(new JProperty("Action", "ComposeMessageByNickname"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetNickname", targetNickname),
                                         new JProperty("Message", targetMessage));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("ComposeMessagePopup", false) as ComposeMessagePopup).ShowLoadingCircle(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnComposeMessageByNickname != null)
                {
                    OnComposeMessageByNickname(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void ReadMessage(int inboxID)
    {
        StartCoroutine(ServerReadMessage(inboxID));
    }

    public IEnumerator ServerReadMessage(int inboxID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "ReadMessage"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("InboxID", inboxID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            (PopupManager.GetPopup("InboxPopup", false) as InboxPopup).LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnReadMessage != null)
                {
                    OnReadMessage(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    public void DeleteMessages(List<int> inboxIDs)
    {
        StartCoroutine(ServerDeleteMessages(inboxIDs));
    }

    public IEnumerator ServerDeleteMessages(List<int> inboxIDs)
    {
        JObject SendObject = new JObject(new JProperty("Action", "DeleteMessages"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("InboxIDs", JsonConvert.SerializeObject(inboxIDs)));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            InboxPopup inbox = PopupManager.GetPopup("InboxPopup", false) as InboxPopup;
            if (inbox != null)
                inbox.LoadingCircle.SetActive(false);

            MessagePopup messageP = PopupManager.GetPopup("MessagePopup", false) as MessagePopup;
            if (messageP != null)
                messageP.LoadingCircle.SetActive(false);

            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
                PopupManager.ShowNotificationStorePopup(message);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnDeleteMessages != null)
                {
                    OnDeleteMessages(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }
    #endregion

    #region ACHIEVEMENT

    public void GetPlayerAchievement(int targetID)
    {
        StartCoroutine(ServerGetPlayerAchievement(targetID));
    }

    private IEnumerator ServerGetPlayerAchievement(int targetID)
    {
        JObject SendObject = new JObject(new JProperty("Action", "GetUserAchievement"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetID", targetID));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnGetAchievementList != null)
                {
                    OnGetAchievementList(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }

    #endregion

    #region REFERRAL
    public void InputReferralCode(string referralCode)
    {
        StartCoroutine(ServerInputReferralCode(referralCode));
    }

    private IEnumerator ServerInputReferralCode(string referralCode)
    {
        JObject SendObject = new JObject(new JProperty("Action", "InputReferralCode"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("ReferralCode", referralCode));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
            else
            {
                ////Debug.log(WebServer.text);
                string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));

                //Debug.log(JsonString);

                if (OnInputReferralCodeSuccess != null)
                {
                    OnInputReferralCodeSuccess(JsonString);
                }
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }
    #endregion

    //Report!
    public void SendReportServer(int tId, String tNick, byte reportType)
    {
        StartCoroutine(UserReportSend(tId, tNick, reportType));
    }

    public IEnumerator UserReportSend(int tId, String tNick, byte reportType)
    {
        string reprtType = "00";
        switch (reportType)
        {
            case ReportPlayerPopup.REPORT_TYPE_PICTURE:
                reprtType = "01";
                break;
            case ReportPlayerPopup.REPORT_TYPE_INTRO:
                reprtType = "02";
                break;
            case ReportPlayerPopup.REPORT_TYPE_BEHANIOUR:
                reprtType = "03";
                break;
        }


        JObject SendObject = new JObject(new JProperty("Action", "ReportUser"),
                                         new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
                                         new JProperty("TargetID", tId),
                                         new JProperty("ReportType", reprtType));
        WWWForm WebForm = new WWWForm();
        WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));

        CachedForm = WebForm;
        //		cachedServerLocation = AuthServerLocation;
        //		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
        //		CachedResponse = HandleLogin;
        //		cachedSelectedServer = SelectedServer;
        //		
        WWW WebServer = new WWW(GameDataServerLocation, WebForm);

        yield return WebServer;
        if (string.IsNullOrEmpty(WebServer.error))
        {
            if (WebServer.text.Contains("ErrorCode"))
            {
                ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
                SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
                //JObject ObjectReceived = JObject.Parse(WebServer.text);
                //SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));
            }
        }
        else
        {
            List<string> temp = new List<string>();
            temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            //			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
            SendServerError("E001", temp);
        }
    }
















    public void IOSInApp(string receipt, string pID)
	{
		StartCoroutine(iOSInApp(receipt, pID));
	}
	
	private IEnumerator iOSInApp(string receipt, string productID)
	{
		JObject SendObject = new JObject(new JProperty("Action", "BuyDiamondUseStore"),
		                                 new JProperty("Token", PlayerPrefs.GetString("CapsaSaveData")),
		                                 new JProperty("StoreType", "ios"),
		                                 new JProperty("Data1", receipt),
		                                 new JProperty("Data2", productID),
                                         new JProperty("InternalCode", gameInternalCode));
		                                 
		WWWForm WebForm = new WWWForm();
		WebForm.AddField("Data", EncryptAES(SendObject.ToString(), _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24)));
		
		CachedForm = WebForm;
		//		cachedServerLocation = AuthServerLocation;
		//		ServerManager.Instance.HandleLogin = HandleOnServerLogin;
		//		CachedResponse = HandleLogin;
		//		cachedSelectedServer = SelectedServer;
		//		
		WWW WebServer = new WWW(GameDataServerLocation, WebForm);
		
		
		
		yield return WebServer;
		if (string.IsNullOrEmpty(WebServer.error))
		{
			if (WebServer.text.Contains("ErrorCode"))
			{
				ErrorReport ObjectReceived = JsonConvert.DeserializeObject<ErrorReport>(WebServer.text);
				SendServerError(ObjectReceived.ErrorCode, ObjectReceived.ErrorMessage);
				//JObject ObjectReceived = JObject.Parse(WebServer.text);
				string message = ObjectReceived.ErrorMessage[(int)StringDataManager.ActiveLanguages];
				PopupManager.ShowNotificationStorePopup(message);
				//SendServerError((string)ObjectReceived["ErrorCode"], new List<string>((string[])ObjectReceived["Message"]));

                if (OnIOSBilllingFailed != null)
                    OnIOSBilllingFailed(message);
			}
			else
			{
				//Debug.Log(WebServer.text);
				string JsonString = DecryptAES(WebServer.text, _defaultToken.Substring(0, 44), _defaultToken.Substring(44, 24));
				//Debug.Log(JsonString);
				if (OnIOSBilllingSuccess != null)
					OnIOSBilllingSuccess(JsonString);
			}
		}
		else
		{
			List<string> temp = new List<string>();
			temp.Add("The device is not connected to the Internet. Please check your current Internet connection.");
			//			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
			//			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
			//			temp.Add(StringDataManager.GetString("NOTIFICATION", "INTERNET_UNSTABLE"));
			SendServerError("E001", temp);
		}
	}
}

public enum ConnectionState
{
    Connected,
    Disconnected,
    Reconnecting
}

public class ErrorReport
{
    public string ErrorCode;
    public List<string> ErrorMessage;
}

public class QueuedRequest
{
    public WWWForm QueuedForm;
    public string QueuedServerLocation;
    public event ServerManager.OnResponseReceived QueuedCallback;

    public QueuedRequest(WWWForm form, string serverLocation, ServerManager.OnResponseReceived callback)
    {
        QueuedForm = form;
        QueuedServerLocation = serverLocation;
        QueuedCallback = callback;
    }

    public void ExecuteCallback(string JsonString)
    {
        if (QueuedCallback != null)
        {
            QueuedCallback(JsonString);
        }
    }
}

public enum GenderType
{
    Male,
    Female,
    Other,
}

public class PlayerListData
{
    public int PlayerID;
    public bool isOnline;
    public GenderType Gender;

    public PlayerListData(int id, bool online, GenderType playerGender)
    {
        PlayerID = id;
        isOnline = online;
        Gender = playerGender;
    }
}

public class FriendStatusData
{
    public int PlayerID;
    public string nickname;
    public bool isOnline;
    public bool isInGame;
}

public enum GiftType
{
    Coins,
    Property
}