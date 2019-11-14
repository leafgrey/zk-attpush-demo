package com.zkteco.attpush.att;
import com.zkteco.constant.ConstantStr;
import com.zkteco.util.teco.AttPushUtil;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
	Logger l = Logger.getRootLogger();
	private static Map<String, List<String>> cmdMap = new HashMap<>();
	/**
	 * 1，设备通完电以后第一个发送到后台的请求
	 * 格式为 /iclock/cdata?options=all&language=xxxx&pushver=xxxx
	 */
	@RequestMapping(value = "/cdata", params = {"options", "language", "pushver"})
	public void init(HttpServletRequest request, HttpServletResponse response) {
		myLog.info("1111:" + request.getRemoteAddr() + " " + request.getRequestURL());
		for (String s : request.getParameterMap().keySet()) {
			myLog.info("1111:S:" + s + ":" + request.getParameter(s));
		}
		// String SN, String options, String language, String pushver, @RequestParam(required = false) String PushOptionsFlag
		String sn = request.getParameter("SN");
		String options = request.getParameter("options");
		String language = request.getParameter("language");
		String pushver = request.getParameter("pushver");
		String pushOptionsFlag = request.getParameter("PushOptionsFlag");
		myLog.info("options=" + options + "&language=" + language + "&pushver=" + pushver);
		try {
			myLog.info(sn + "考勤机初始化请求进来了");
			//加载命令
			cmdMap = AttPushUtil.loadCmd(sn);
			myLog.info("初始化信息加载命令map：" + cmdMap);
			String initOptions = AttPushUtil.initOptions(sn, pushOptionsFlag);
			myLog.info("返回给考勤机的初始化参数为：" + initOptions);
			//返回成功以后设备会发送心跳请求
			response.getWriter().write(initOptions);
			// 当返回这个的时候，设备会每2秒重新发本请求，直到返回OK为止
			//response.getWriter().write("UNKNOWN DEVICE")
		}
		catch (IOException e) {
			myLog.error(e.getMessage(), e);
		}
	}
	/**
	 * 2，心跳请求，会从服务器拿到命令返回给设备
	 */
	@RequestMapping("/getrequest")
	public void heartBeat(HttpServletRequest request, HttpServletResponse response) {
		myLog.info("2222:" + request.getRemoteAddr() + " " + request.getRequestURL());
		for (String s : request.getParameterMap().keySet()) {
			myLog.info("2222:S:" + s + ":" + request.getParameter(s));
		}
		String sn = request.getParameter("SN");
		StringBuilder sb = new StringBuilder("OK");
		List<String> cmds = cmdMap.get(sn);
		//等于空说明从来没加载过，如果初始化加载过了，此时应该不为Null 只是size为0
		if(cmds == null) {
			myLog.error("未初始化的设备SN：" + sn);
			cmdMap = AttPushUtil.loadCmd(sn);
			if(cmdMap == null) {
				myLog.error("命令列表为空 null");
			}
			else {
				cmds = cmdMap.get(sn);
			}
		}
		if(cmds != null && cmds.size() > 0) {
			//如果有命令就不返回OK了
			sb.setLength(0);
			// cmds.stream().forEach(cmd -> sb.append(cmd).append("\r\n\r\n"))
			for (String cmd : cmds) {
				sb.append(cmd).append("\r\n\r\n");
			}
		}
		myLog.info("回复心跳的结果：" + sb);
		try {
			//处理完以后立刻将集合清空，实际开发中应该是在/devicecmd这个请求里完成
			// cmdMap.get(sn).clear();
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
		myLog.info("3333:" + request.getRemoteAddr() + " " + request.getRequestURL());
		for (String s : request.getParameterMap().keySet()) {
			myLog.info("3333:S:" + s + ":" + request.getParameter(s));
		}
		myLog.info("考勤机心跳请求进来了ping：" + new SimpleDateFormat("HH:mm:ss").format(new Date()));
		try {
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			myLog.error(e.getMessage(), e);
		}
	}
	/**
	 * 4，设备端处理完命令以后会发送该请求，告诉服务器命令的处理结果
	 */
	@RequestMapping("/devicecmd")
	public void handleCmd(@RequestBody String data, HttpServletRequest request, HttpServletResponse response) {
		myLog.info("4444:" + request.getRemoteAddr() + " " + request.getRequestURL());
		for (String s : request.getParameterMap().keySet()) {
			myLog.info("4444:S:" + s + ":" + request.getParameter(s));
		}
		String sn = request.getParameter("SN");
		myLog.info(sn + "设备处理完命令以后的返回结果：" + data);
		try {
			// TODO
			cmdMap.get(sn).clear();
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 5 table 数据
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @param table String
	 */
	@RequestMapping(value = "/cdata", params = "table")
	public void handleRtData(HttpServletRequest request, HttpServletResponse response, String table) {
		myLog.info("5555:" + request.getRemoteAddr() + " " + request.getRequestURL());
		for (String s : request.getParameterMap().keySet()) {
			myLog.info("5555:S:" + s + ":" + request.getParameter(s));
		}
		String sn = request.getParameter("SN");
		myLog.info(sn + " 设备上传上来的数据 table:" + table);
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
		// "ATTPHOTO"
		if(ConstantStr.ATT_PHOTO.equals(table)) {
			//如果是考勤图片上传,此处重点演示如何处理上传到软件里的照片
			try {
				// String photo = getAttPhotoDataInfo(data, bos.toByteArray()).getString("static/photo")
				String photo = null;
				if(bos != null) {
					photo = Objects.requireNonNull(AttPushUtil.getAttPhotoDataInfo(data, bos.toByteArray())).getString("static/photo");
				}
				AttPushUtil.saveFile(System.currentTimeMillis() + ".jpg", photo);
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
		}
		// "OPERLOG"
		if(ConstantStr.OPER_LOG.equals(table)) {
			//处理人员上传上拉的比对照片模板 "BIOPHOTO"
			if(data.startsWith(ConstantStr.BIO_PHOTO)) {
				Map<String, String> preInfoMap = AttPushUtil.formatStrToMap(data, "\t");
				myLog.info("preInfoMap:" + preInfoMap);
				//处理上传上来的比对照片
				AttPushUtil.saveFile(System.currentTimeMillis() + ".jpg", preInfoMap.get("Content"));
			}
		}
		try {
			response.getWriter().write("OK");
		}
		catch (IOException e) {
			myLog.error(e.getMessage(), e);
		}
	}
}
