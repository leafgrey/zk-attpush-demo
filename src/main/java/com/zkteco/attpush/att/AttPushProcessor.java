package com.zkteco.attpush.att;
import com.google.common.collect.Maps;
import com.zkteco.constant.ConstantStr;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
/**
 * 所有的设备请求都会在url参数里携带SN，这是设备序列号(serial number的缩写)，每个设备唯一标识
 * @author zch
 */
@Controller
@RequestMapping("/iclock")
public class AttPushProcessor {
	private final static Logger myLog = Logger.getRootLogger();
	private static Map<String, List<String>> cmdMap = new HashMap<>();
	private static Map<String, String> formateStrToMap(String data, String split) {
		Map<String, String> dataMap = Maps.newHashMapWithExpectedSize(3);
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
	private static void setKeyValue(JSONObject json, String key, Object value) {
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
	private static void saveFile(String fileName, String base64ImgStr) {
		try {
			//如果没有D盘，需要修改一下图片存放位置
			String fullPath = "D://photo";
			File file = new File(new File(fullPath), fileName);
			if(!file.getParentFile().exists()) {
				boolean idMkdir = file.getParentFile().mkdirs();
				myLog.debug(idMkdir);
			}
			if(file.exists()) {
				boolean isDelete = file.delete();
				myLog.debug(isDelete);
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
	/**
	 * 1，设备通完电以后第一个发送到后台的请求
	 * 格式为 /iclock/cdata?options=all&language=xxxx&pushver=xxxx
	 */
	@RequestMapping(value = "/cdata", params = {"options", "language", "pushver"})
	public void init(HttpServletRequest request, HttpServletResponse response) {
		// /iclock/cdata?SN=CJDE193560303&options=all&language=83&pushver=2.4.0&PushOptionsFlag=1 HTTP/1.1
		System.out.println("1111:" + request.getRemoteAddr() + " " + request.getRequestURL());
		/* ============================================ */
		for (String s : request.getParameterMap().keySet()) {
			System.out.println("1111S:" + s + ":" + request.getParameter(s));
		}
		/* ============================================ */
		String sn = request.getParameter("SN");
		String options = request.getParameter("options");
		String language = request.getParameter("language");
		String pushver = request.getParameter("pushver");
		String pushOptionsFlag = request.getParameter("PushOptionsFlag");
		System.out.println("options=" + options + "....language=" + language + "....pushver=" + pushver);
		try {
			System.out.println("考勤机初始化请求进来了......");
			//加载命令
			loadCmd(sn);
			String initOptions = initOptions(sn, pushOptionsFlag);
			System.out.println("返回给考勤机的初始化参数为...." + initOptions);
			//返回成功以后设备会发送心跳请求
			response.getWriter().write(initOptions);
			// 当返回这个的时候，设备会每2秒重新发本请求，直到返回OK为止
			// response.getWriter().write("UNKNOWN DEVICE")
		}
		catch (IOException e) {
			myLog.error(e.getMessage(), e);
		}
	}
	/**
	 * 2，心跳请求，会从服务器拿到命令返回给设备
	 */
	@RequestMapping("/getrequest")
	public void heartBeat(String sn, HttpServletResponse response, HttpServletRequest request) {
		System.out.println("2222:" + request.getRemoteAddr() + " " + request.getRequestURL());
		/* ============================================ */
		for (String s : request.getParameterMap().keySet()) {
			System.out.println("2222S:" + s + ":" + request.getParameter(s));
		}
		/* ============================================ */
		System.out.println(request.getParameter("SN"));
		StringBuffer sb = new StringBuffer("OK");
		List<String> cmds = cmdMap.get(sn);
		//等于空说明从来没加载过，如果初始化加载过了，此时应该不为Null 只是size为0
		if(cmds == null) {
			loadCmd(sn);
			cmds = cmdMap.get(sn);
		}
		if(cmds != null && cmds.size() > 0) {
			//如果有命令就不返回OK了
			sb.setLength(0);
			// 原代码
			// cmds.stream().forEach(cmd -> sb.append(cmd).append("\r\n\r\n"))
			// 20191112 zch 修改
			cmds.forEach(cmd -> sb.append(cmd).append("\r\n\r\n"));
		}
		System.out.println("心跳的返回结果为...." + sb);
		try {
			//处理完以后立刻将集合清空，实际开发中应该是在/devicecmd这个请求里完成
			cmdMap.get(sn).clear();
			response.getWriter().write(sb.toString());
		}
		catch (IOException e) {
			myLog.error(e.getMessage(), e);
		}
	}
	/**
	 * 3，候补心跳请求，正常情况下设备不发此请求，有大量数据上传的时候，不发上面的心跳，发这个请求，
	 * 这个请求，服务器只能返回OK，不可以返回命令
	 */
	@RequestMapping("/ping")
	public void ping(HttpServletRequest request, HttpServletResponse response) {
		System.out.println("3333:" + request.getRemoteAddr() + " " + request.getRequestURL());
		/* ============================================ */
		for (String s : request.getParameterMap().keySet()) {
			System.out.println("3333S:" + s + ":" + request.getParameter(s));
		}
		/* ============================================ */
		System.out.println("考勤机心跳请求进来了......ping" + new SimpleDateFormat("HH:mm:ss").format(new Date()));
		try {
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 4，设备端处理完命令以后会发送该请求，告诉服务器命令的处理结果
	 */
	@RequestMapping("/devicecmd")
	public void handleCmd(String sn, @RequestBody String data, HttpServletRequest request, HttpServletResponse response) {
		System.out.println("4444:" + request.getRemoteAddr() + " " + request.getRequestURL());
		/* ============================================ */
		for (String s : request.getParameterMap().keySet()) {
			System.out.println("4444S:" + s + ":" + request.getParameter(s));
		}
		/* ============================================ */
		System.out.println(sn + "设备处理完命令以后的返回结果..." + data);
		try {
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	@RequestMapping(value = "/cdata", params = "table")
	public void handleRtData(HttpServletRequest request, HttpServletResponse response, String sn, String table) {
		System.out.println("5555:" + request.getRemoteAddr() + " " + request.getRequestURL());
		/* ============================================ */
		for (String s : request.getParameterMap().keySet()) {
			System.out.println("5555S:" + s + ":" + request.getParameter(s));
		}
		/* ============================================ */
		//System.out.println("设备上传上来的数据...."+data+"....table..."+table)
		System.out.println(sn + "设备上传上来的数据...table..." + table);
		String data = "";
		ByteArrayOutputStream bos = null;
		byte[] b = new byte[1024];
		try {
			InputStream is = request.getInputStream();
			bos = new ByteArrayOutputStream();
			int len;
			while ((len = is.read(b)) != -1) {
				bos.write(b, 0, len);
			}
			data = new String(bos.toByteArray());
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		if(ConstantStr.ATT_PHOTO.equals(table)) {
			//如果是考勤图片上传,此处重点演示如何处理上传到软件里的照片
			try {
				// String photo = getAttPhotoDataInfo(data, bos.toByteArray()).getString("static/photo")
				String photo = null;
				if(bos != null) {
					photo = Objects.requireNonNull(getAttPhotoDataInfo(data, bos.toByteArray())).getString("static/photo");
				}
				saveFile(System.currentTimeMillis() + ".jpg", photo);
			}
			catch (JSONException e) {
				myLog.error(e.getMessage(), e);
			}
		}
		if(ConstantStr.OPER_LOG.equals(table)) {
			//处理人员上传上拉的比对照片模板
			if(data.startsWith(ConstantStr.BIO_PHOTO)) {
				Map<String, String> preInfoMap = formateStrToMap(data, "\t");
				System.out.println("preInfoMap..." + preInfoMap);
				//处理上传上来的比对照片
				saveFile(System.currentTimeMillis() + ".jpg", preInfoMap.get("Content"));
			}
		}
		try {
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void loadCmd(String sn) {
		try {
			List<String> cmdList = new ArrayList<>();
			File file = ResourceUtils.getFile("classpath:cmd.txt");
			BufferedReader br = new BufferedReader(new FileReader(file));
			String cmd;
			while ((cmd = br.readLine()) != null) {
				if(!cmd.startsWith("#")) {
					System.out.println("加载进内存的命令是...." + cmd);
					cmdList.add(cmd);
				}
			}
			cmdMap.put(sn, cmdList);
		}
		catch (Exception e) {
			myLog.error(e.getMessage(), e);
		}
	}
	/**
	 * 设备通电以后连接到服务器，需要返回的初始化参数
	 * @param sn String
	 * @param pushOptionsFlag String
	 * @return String
	 */
	private String initOptions(String sn, String pushOptionsFlag) {
		StringBuilder devOptions = new StringBuilder();
		devOptions.append("GET OPTION FROM: ").append(sn);
		// + "\nStamp=" + devInfo.devInfoJson.getString("Stamp")
		devOptions.append("\nATTLOGStamp=0");
		devOptions.append("\nOPERLOGStamp=0");
		devOptions.append("\nBIODATAStamp=0");
		devOptions.append("\nATTPHOTOStamp=0");
		//断网重连
		devOptions.append("\nErrorDelay=10");
		//心跳间隔
		devOptions.append("\nDelay=5");
		//时区
		devOptions.append("\nTimeZone=8");
		//实时上传
		devOptions.append("\nRealtime=1");
		if(ConstantStr.NUM_STR1.equals(pushOptionsFlag)) {
			// 支持参数单独获取的才要把需要获取的参数回传给设备 modifeid by max 20170926
			devOptions.append("\nPushOptions=RegDeviceType,FingerFunOn,FaceFunOn,FPVersion,FaceVersion,NetworkType,HardwareId3,HardwareId5,HardwareId56,LicenseStatus3,LicenseStatus5,LicenseStatus56");
		}
		devOptions.append("\n");
		return devOptions.toString();
	}
	private JSONObject getAttPhotoDataInfo(String data, byte[] bufArray) {
		if(StringUtils.isNotBlank(data)) {
			String[] headArray = data.split("CMD=uploadphoto", 2);
			JSONObject jsonData = new JSONObject();
			Map<String, String> preInfoMap = formateStrToMap(headArray[0], "\n");
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
}
