package com.tencent.ilivedemo.demos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tencent.TIMMessage;
import com.tencent.TIMUserProfile;
import com.tencent.av.sdk.AVVideoCtrl;
import com.tencent.ilivedemo.R;
import com.tencent.ilivedemo.model.Constants;
import com.tencent.ilivedemo.model.MessageObservable;
import com.tencent.ilivedemo.model.StatusObservable;
import com.tencent.ilivedemo.model.UserInfo;
import com.tencent.ilivedemo.uiutils.DemoFunc;
import com.tencent.ilivedemo.uiutils.DlgMgr;
import com.tencent.ilivedemo.view.DemoEditText;
import com.tencent.ilivesdk.ILiveCallBack;
import com.tencent.ilivesdk.ILiveConstants;
import com.tencent.ilivesdk.ILiveSDK;
import com.tencent.ilivesdk.core.ILiveLoginManager;
import com.tencent.ilivesdk.core.ILiveRoomManager;
import com.tencent.ilivesdk.view.AVRootView;
import com.tencent.livesdk.ILVCustomCmd;
import com.tencent.livesdk.ILVLiveConfig;
import com.tencent.livesdk.ILVLiveConstants;
import com.tencent.livesdk.ILVLiveManager;
import com.tencent.livesdk.ILVLiveRoomOption;
import com.tencent.livesdk.ILVText;

/**
 * Created by xkazerzhang on 2017/5/24.
 */
public class DemoHost extends Activity implements View.OnClickListener, ILVLiveConfig.ILVLiveMsgListener, ILiveLoginManager.TILVBStatusListener{
    private final String TAG = "DemoHost";
    private DemoEditText etRoom;
    private TextView tvMsg;
    private ScrollView svScroll;
    private AVRootView arvRoot;

    private boolean isCameraOn = true;
    private boolean isMicOn = true;
    private boolean isFlashOn = false;

    private String strMsg = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo_host);

        UserInfo.getInstance().getCache(getApplicationContext());

        arvRoot = (AVRootView)findViewById(R.id.arv_root);
        etRoom = (DemoEditText)findViewById(R.id.et_room);
//        etRoom.setText(""+UserInfo.getInstance().getRoom());
        tvMsg = (TextView)findViewById(R.id.tv_msg);
        svScroll = (ScrollView)findViewById(R.id.sv_scroll);

        ILVLiveManager.getInstance().setAvVideoView(arvRoot);
        MessageObservable.getInstance().addObserver(this);
        StatusObservable.getInstance().addObserver(this);

        arvRoot.setAutoOrientation(false);
        // 打开摄像头预览
        arvRoot.setSubCreatedListener(new AVRootView.onSubViewCreatedListener() {
            @Override
            public void onSubViewCreated() {
                ILiveRoomManager.getInstance().enableCamera(ILiveConstants.FRONT_CAMERA, true);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ILVLiveManager.getInstance().onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ILVLiveManager.getInstance().onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ILiveConstants.NONE_CAMERA != ILiveRoomManager.getInstance().getActiveCameraId()){
            ILiveRoomManager.getInstance().enableCamera(ILiveRoomManager.getInstance().getActiveCameraId(), false);
        }
        MessageObservable.getInstance().deleteObserver(this);
        StatusObservable.getInstance().deleteObserver(this);
        ILVLiveManager.getInstance().onDestory();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.tv_create:
                createRoom();
                break;
            case R.id.iv_camera:
                isCameraOn = !isCameraOn;
                ILiveRoomManager.getInstance().enableCamera(ILiveRoomManager.getInstance().getCurCameraId(),
                        isCameraOn);
                ((ImageView)findViewById(R.id.iv_camera)).setImageResource(
                        isCameraOn ? R.mipmap.ic_camera_on : R.mipmap.ic_camera_off);
                break;
            case R.id.iv_switch:
                Log.v(TAG, "switch->cur: "+ILiveRoomManager.getInstance().getActiveCameraId()+"/"+ILiveRoomManager.getInstance().getCurCameraId());
                if (ILiveConstants.NONE_CAMERA != ILiveRoomManager.getInstance().getActiveCameraId()) {
                    ILiveRoomManager.getInstance().switchCamera(1 - ILiveRoomManager.getInstance().getActiveCameraId());
                }else{
                    ILiveRoomManager.getInstance().switchCamera(ILiveConstants.FRONT_CAMERA);
                }
                break;
            case R.id.iv_flash:
                toggleFlash();
                break;
            case R.id.iv_mic:
                isMicOn = !isMicOn;
                ILiveRoomManager.getInstance().enableMic(isMicOn);
                ((ImageView)findViewById(R.id.iv_mic)).setImageResource(
                        isMicOn ? R.mipmap.ic_mic_on : R.mipmap.ic_mic_off);
                break;
            case R.id.iv_return:
                finish();
                break;
        }
    }

    @Override
    public void onNewTextMsg(ILVText text, String SenderId, TIMUserProfile userProfile) {
        addMessage(SenderId, DemoFunc.getLimitString(text.getText(), Constants.MAX_SIZE));
    }

    @Override
    public void onNewCustomMsg(ILVCustomCmd cmd, String id, TIMUserProfile userProfile) {
        switch (cmd.getCmd()){
            case ILVLiveConstants.ILVLIVE_CMD_LINKROOM_REQ:     // 跨房邀请
                linkRoomReq(id);
                break;
        }
    }

    @Override
    public void onNewOtherMsg(TIMMessage message) {

    }

    @Override
    public void onForceOffline(int error, String message) {
        finish();
    }

    private Context getContenxt(){
        return DemoHost.this;
    }

    // 添加消息
    private void addMessage(String sender, String msg){
        strMsg += "["+sender+"]  "+msg+"\n";
        tvMsg.setText(strMsg);
        svScroll.fullScroll(View.FOCUS_DOWN);
    }

    private void joinRoom(){
        final int roomId = DemoFunc.getIntValue(etRoom.getText().toString(), -1);
        if (-1 == roomId){
            DlgMgr.showMsg(getContenxt(), getString(R.string.str_tip_num_error));
            return;
        }
        ILVLiveRoomOption option = new ILVLiveRoomOption("")
                .autoCamera(ILiveConstants.NONE_CAMERA != ILiveRoomManager.getInstance().getActiveCameraId())
                .videoMode(ILiveConstants.VIDEOMODE_NORMAL)
                .controlRole(Constants.ROLE_LIVEGUEST)
                .autoFocus(true);
        ILVLiveManager.getInstance().joinRoom(roomId, option, new ILiveCallBack() {
            @Override
            public void onSuccess(Object data) {
                afterCreate();
            }

            @Override
            public void onError(String module, int errCode, String errMsg) {
                DlgMgr.showMsg(getContenxt(), "create failed:"+module+"|"+errCode+"|"+errMsg);
            }
        });
    }

    private void showChoiceDlg(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("房间已存在，是否加入房间？")
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        joinRoom();
                        dialogInterface.dismiss();
                    }
                });
        DlgMgr.showAlertDlg(this, builder);
    }

    // 加入房间
    private void createRoom(){
        final int roomId = DemoFunc.getIntValue(etRoom.getText().toString(), -1);
        if (-1 == roomId){
            DlgMgr.showMsg(getContenxt(), getString(R.string.str_tip_num_error));
            return;
        }
        ILVLiveRoomOption option = new ILVLiveRoomOption(ILiveLoginManager.getInstance().getMyUserId())
                .autoCamera(ILiveConstants.NONE_CAMERA != ILiveRoomManager.getInstance().getActiveCameraId())
                .videoMode(ILiveConstants.VIDEOMODE_NORMAL)
                .controlRole(Constants.ROLE_MASTER)
                .autoFocus(true);
        ILVLiveManager.getInstance().createRoom(roomId,
                option, new ILiveCallBack() {
                    @Override
                    public void onSuccess(Object data) {
                        afterCreate();
                    }

                    @Override
                    public void onError(String module, int errCode, String errMsg) {
                        if (module.equals(ILiveConstants.Module_IMSDK) && 10021 == errCode){
                            // 被占用，改加入
                            showChoiceDlg();
                        }else {
                            DlgMgr.showMsg(getContenxt(), "create failed:" + module + "|" + errCode + "|" + errMsg);
                        }
                    }
                });
    }

    private void afterCreate(){
        UserInfo.getInstance().setRoom(ILiveRoomManager.getInstance().getRoomId());
        UserInfo.getInstance().writeToCache(this);
        etRoom.setEnabled(false);
        findViewById(R.id.tv_create).setVisibility(View.INVISIBLE);
        findViewById(R.id.iv_camera).setVisibility(View.VISIBLE);
        findViewById(R.id.iv_flash).setVisibility(View.VISIBLE);
        findViewById(R.id.iv_mic).setVisibility(View.VISIBLE);
    }

    private void toggleFlash(){
        if (ILiveConstants.BACK_CAMERA != ILiveRoomManager.getInstance().getActiveCameraId()){
            return;
        }
        AVVideoCtrl videoCtrl = ILiveSDK.getInstance().getAvVideoCtrl();
        if (null == videoCtrl) {
            return;
        }

        final Object cam = videoCtrl.getCamera();
        if ((cam == null) || (!(cam instanceof Camera))) {
            return;
        }
        final Camera.Parameters camParam = ((Camera) cam).getParameters();
        if (null == camParam) {
            return;
        }

        Object camHandler = videoCtrl.getCameraHandler();
        if ((camHandler == null) || (!(camHandler instanceof Handler))) {
            return;
        }

        //对摄像头的操作放在摄像头线程
        if (isFlashOn == false) {
            ((Handler) camHandler).post(new Runnable() {
                public void run() {
                    try {
                        camParam.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        ((Camera) cam).setParameters(camParam);
                        isFlashOn = true;
                    } catch (RuntimeException e) {
                        Log.d(TAG, "setParameters->RuntimeException");
                    }
                }
            });
        } else {
            ((Handler) camHandler).post(new Runnable() {
                public void run() {
                    try {
                        camParam.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        ((Camera) cam).setParameters(camParam);
                        isFlashOn = false;
                    } catch (RuntimeException e) {
                        Log.d(TAG, "setParameters->RuntimeException");
                    }
                }
            });
        }
    }

    // 拒绝跨房连麦
    private void refuseLink(String id){
        ILVLiveManager.getInstance().refuseLinkRoom(id, null);
    }

    // 同意跨房连麦
    private void acceptLink(String id){
        ILVLiveManager.getInstance().acceptLinkRoom(id, null);
    }

    private void linkRoomReq(final String id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.live_title_link);
        builder.setMessage("["+id+"]"+getString(R.string.link_req_tips));
        builder.setNegativeButton(R.string.str_btn_refuse, new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                refuseLink(id);
            }
        });
        builder.setPositiveButton(R.string.str_btn_agree, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                acceptLink(id);
            }
        });
        DlgMgr.showAlertDlg(this, builder);
    }
}
