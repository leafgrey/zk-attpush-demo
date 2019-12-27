package com.zkteco.util.teco;
import com.google.common.collect.Maps;
import com.zkteco.constant.ConstantStr;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/***
 * @class AttPushUtil
 * @description AttPushProcessor 工具类
 * @author zch
 * @date 2019/11/13
 * @version V0.0.1.201911131137.01
 * @modfiyDate 201911131137
 * @createDate 201911131137
 * @package com.zkteco.util.teco
 */
public class AttPushUtil {
	private final static Logger myLog = Logger.getRootLogger();
	/**
	 * loadCmd
	 * @param sn String
	 */
	public static Map<String, List<String>> loadCmd(String sn) {
		try {
			Map<String, List<String>> cmdMap = new HashMap<>();
			List<String> cmdList = new ArrayList<>();
			File file = ResourceUtils.getFile("classpath:cmd.txt");
			BufferedReader br = new BufferedReader(new FileReader(file));
			String cmd;
			while ((cmd = br.readLine()) != null) {
				if(!cmd.startsWith("#")) {
					myLog.info("加载进内存的命令是...." + cmd);
					cmdList.add(cmd);
				}
			}
			cmdMap.put(sn, cmdList);
			return cmdMap;
		}
		catch (Exception e) {
			myLog.error(e.getMessage(), e);
			return null;
		}
	}
	/**
	 * 设备通电以后连接到服务器，需要返回的初始化参数
	 * @param sn String
	 * @param pushOptionsFlag String
	 * @return String
	 */
	public static String initOptions(String sn, String pushOptionsFlag) {
		StringBuilder devOptions = new StringBuilder();
		devOptions.append("GET OPTION FROM: ").append(sn);
		// + "\nStamp=" + devInfo.devInfoJson.getString("Stamp")
		devOptions.append("\nATTLOGStamp=0");
		devOptions.append("\nOPERLOGStamp=0");
		devOptions.append("\nBIODATAStamp=0");
		devOptions.append("\nATTPHOTOStamp=0");
		//断网重连
		devOptions.append("\nErrorDelay=60");
		//心跳间隔
		devOptions.append("\nDelay=60");
		//时区
		devOptions.append("\nTimeZone=8");
		//实时上传
		devOptions.append("\nRealtime=1");
		//服务器版本
		devOptions.append("\nPushProtVer=2.3.0");
		//是否加密
		devOptions.append("\nEncryptFlag=0");
		if(ConstantStr.NUM_STR1.equals(pushOptionsFlag)) {
			// 支持参数单独获取的才要把需要获取的参数回传给设备 modifeid by max 20170926
			// 软件需要设备推送的参数列表
			// RegDeviceType,
			// FingerFunOn,
			// FaceFunOn,
			// FPVersion,
			// FaceVersion,
			// NetworkType,
			// HardwareId3,
			// HardwareId5,
			// HardwareId56,
			// LicenseStatus3,
			// LicenseStatus5,
			// LicenseStatus56
			devOptions.append("\nPushOptions=RegDeviceType,FingerFunOn,FaceFunOn,FPVersion,FaceVersion,NetworkType,HardwareId3,HardwareId5,HardwareId56,LicenseStatus3,LicenseStatus5,LicenseStatus56");
		}
		devOptions.append("\n");
		return devOptions.toString();
	}
	/**
	 * getAttPhotoDataInfo
	 * @param data String
	 * @param bufArray byte[]
	 * @return JSONObject
	 */
	public static JSONObject getAttPhotoDataInfo(String data, byte[] bufArray) {
		if(StringUtils.isNotBlank(data)) {
			String[] headArray = data.split("CMD=uploadphoto", 2);
			JSONObject jsonData = new JSONObject();
			Map<String, String> preInfoMap = formatStrToMap(headArray[0], "\n");
			for (String key : preInfoMap.keySet()) {
				setKeyValue(jsonData, key, preInfoMap.get(key));
			}
			int photoBufLength = Integer.parseInt(preInfoMap.get("size"));
			byte[] fileDataBytesBase64 = Base64.encodeBase64(ArrayUtils.subarray(bufArray, bufArray.length - photoBufLength, photoBufLength));
			String fileDataStrBase64 = new String(fileDataBytesBase64);
			setKeyValue(jsonData, "photo", fileDataStrBase64);
			setKeyValue(jsonData, "static/photo", fileDataStrBase64);
			return jsonData;
		}
		else {
			return null;
		}
	}
	/**
	 * formateStrToMap
	 * @param data String
	 * @param split String
	 * @return Map<String, String>
	 */
	public static Map<String, String> formatStrToMap(@NotNull String data, String split) {
		Map<String, String> dataMap = Maps.newHashMapWithExpectedSize(2);
		String[] dataArray = data.split(split);
		for (String d : dataArray) {
			String[] oneData = d.split("=", 2);
			if(oneData.length == 2) {
				dataMap.put(oneData[0], oneData[1].replace("\n", ""));
			}
			else if(oneData.length == 1) {
				dataMap.put(oneData[0], "");
			}
		}
		return dataMap;
	}
	/**
	 * setKeyValue
	 * @param json JSONObject
	 * @param key String
	 * @param value Object
	 */
	public static void setKeyValue(@NotNull JSONObject json, String key, Object value) {
		try {
			json.putOpt(key, value);
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 保存base64图片
	 * @param fileName String
	 * @param base64ImgStr String
	 */
	public static void saveFile(String fileName, String base64ImgStr) {
		try {
			//如果没有D盘，需要修改一下图片存放位置
			String fullPath = "D://photo";
			File file = new File(new File(fullPath), fileName);
			if(!file.getParentFile().exists()) {
				boolean isMkdir = file.getParentFile().mkdirs();
				myLog.info(isMkdir);
			}
			if(file.exists()) {
				boolean isDeleted = file.delete();
				myLog.info(isDeleted);
			}
			OutputStream out = new FileOutputStream(file);
			out.write(java.util.Base64.getDecoder().decode(base64ImgStr));
			out.flush();
			out.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
