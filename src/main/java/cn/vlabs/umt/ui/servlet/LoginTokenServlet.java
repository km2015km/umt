/*
 * Copyright (c) 2008-2016 Computer Network Information Center (CNIC), Chinese Academy of Sciences.
 * 
 * This file is part of Duckling project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 *
 */
package cn.vlabs.umt.ui.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.beans.factory.BeanFactory;

import cn.vlabs.umt.common.util.CommonUtils;
import cn.vlabs.umt.common.util.RandomUtil;
import cn.vlabs.umt.common.util.RequestUtil;
import cn.vlabs.umt.domain.OauthLog;
import cn.vlabs.umt.services.account.IOauthLogService;
import cn.vlabs.umt.services.role.RoleService;
import cn.vlabs.umt.services.role.UMTRole;
import cn.vlabs.umt.services.user.LoginService;
import cn.vlabs.umt.services.user.UserService;
import cn.vlabs.umt.services.user.bean.LoginInfo;
import cn.vlabs.umt.services.user.bean.OauthClientBean;
import cn.vlabs.umt.services.user.bean.OauthToken;
import cn.vlabs.umt.services.user.bean.Token;
import cn.vlabs.umt.services.user.bean.TokenLoginCredential;
import cn.vlabs.umt.services.user.bean.User;
import cn.vlabs.umt.services.user.service.IOauthClientService;
import cn.vlabs.umt.services.user.service.IOauthTokenService;
import cn.vlabs.umt.services.user.service.ITokenService;
import cn.vlabs.umt.ui.Attributes;
import cn.vlabs.umt.ui.UMTContext;

/**
 * @author lvly
 * @since 2013-9-13
 */
public class LoginTokenServlet extends HttpServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4979947905914747000L;
	private static final Logger LOGGER=Logger.getLogger(LoginTokenServlet.class);
	private IOauthTokenService oauthTokenService;
	private ITokenService tokenService;
	private LoginService loginService;
	private RoleService rs ;
	private IOauthClientService clientServer;
	private IOauthLogService logService;
	private UserService userService;
	
	public LoginTokenServlet(){
	    super();
	}
	
	public void init(){
	    BeanFactory factory = (BeanFactory) getServletContext().getAttribute(Attributes.APPLICATION_CONTEXT_KEY);
		oauthTokenService = (IOauthTokenService)factory.getBean(IOauthTokenService.BEAN_ID);
		tokenService=(ITokenService)factory.getBean(ITokenService.BEAN_ID);
		loginService=(LoginService)factory.getBean(LoginService.BEAN_ID);
		rs=(RoleService) factory.getBean(RoleService.BEAN_ID);
		clientServer=(IOauthClientService)factory.getBean(IOauthClientService.BEAN_ID);
		logService=(IOauthLogService)factory.getBean(IOauthLogService.BEAN_ID);
		userService=(UserService)factory.getBean(UserService.BEAN_ID);
	}
	
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String action=request.getParameter("action");
		if("apply".equals(action)){
			apply(request,response);
		}else if("login".equals(action)){
			login(request,response);
		}else{
			error(response,"not such action["+action+"]",null);
		}
		
	}
	
	/**
	 * 使用token并登陆
	 * */
	private void login(HttpServletRequest request, HttpServletResponse response)throws IOException {
		String token=request.getParameter("token");
		String returnUrl=request.getParameter("returnUrl");
		OauthLog oauthLog=new OauthLog();
		oauthLog.setAction(OauthLog.ACTION_VALIDATE_TOKEN_LOGIN);
		oauthLog.setIp(RequestUtil.getRemoteIP(request));
		oauthLog.setUserAgent(request.getHeader("User-Agent"));
		if(CommonUtils.isNull(token)){
			oauthLog.setResult(OauthLog.RESULT_TOKEN_NOT_FOUND);
			error(response,"token is null",oauthLog);
			return;
		}
		if(CommonUtils.isNull(returnUrl)){
			oauthLog.setResult(OauthLog.RESULT_REDIRECT_URL_ERROR);
			error(response,"returnUrl is null",oauthLog);
			return;
		}
		String tokenInfo[]=token.split("-");
		if(tokenInfo.length!=2){
			oauthLog.setResult(OauthLog.RESULT_TOKEN_FORMAT_ERROR);
			error(response,"token is unvalid["+token+"]",oauthLog);
			return;
		}
		int tokenId=Integer.parseInt(tokenInfo[1]);
		if(!tokenService.isValid(tokenId, tokenInfo[0], Token.OPERATION_WEB_LOGIN)){
			oauthLog.setResult(OauthLog.RESULT_TOKEN_EXPIRED);
			error(response,"token is expired or can't find["+token+"]",oauthLog);
			return;
		}
		Token tokenObj=tokenService.getTokenById(tokenId);
		LoginInfo info=loginService.loginAndReturnPasswordType(new TokenLoginCredential(tokenObj));
		if(info.getUser()==null){
			oauthLog.setResult(OauthLog.RESULT_USER_NOT_FOUND);
			error(response,"can't find user ,login error",oauthLog);
			return;
		}
		try {
			JSONObject obj=new JSONObject(tokenObj.getContent());
			oauthLog.setClientId(obj.getString("clientId"));
		} catch (JSONException e) {
			LOGGER.error(e.getMessage(),e);
		}
		OauthClientBean bean=clientServer.findByClientId(oauthLog.getClientId());
		if(bean==null){
			oauthLog.setResult(OauthLog.RESULT_CLIENT_ID_ERROR);
			error(response, "cant found client that id is "+oauthLog.getClientId(), oauthLog);
			return;
		}
		oauthLog.setClientId(bean.getClientId());
		oauthLog.setClientName(bean.getClientName());
		oauthLog.setUid(info.getUser().getId());
		oauthLog.setCstnetId(info.getUser().getCstnetId());
		oauthLog.setResult(OauthLog.RESULT_SUCCESS);
		logService.addLog(oauthLog);
		tokenService.toUsed(tokenObj.getId());
		UMTContext.saveUser(request.getSession(), info);
		UMTRole[] roles = rs.getUserRoles(info.getUser().getCstnetId());
		UMTContext.saveRoles(request.getSession(), roles);
		response.sendRedirect(setHash2Url(returnUrl));
	}
	
	private String setHash2Url(String url){
		int index=url.indexOf("#");
		String result=url;
		if(index>-1){
			String prefix=url.substring(0,index);
			String suffix=url.substring(index+1);
			prefix=addParam(prefix,"hash",suffix);
			return prefix+"#"+suffix;
		}
		return result;
	}
	private String addParam(String url,String key,String value){
		if(CommonUtils.isNull(url)||CommonUtils.isNull(key)||CommonUtils.isNull(value)){
			return url;
		}else if(url.contains(key+"=")){
			return url;
		}else{
			if(url.contains("?")){
				return url+"&"+key+"="+value;
			}else{
				return url+"?"+key+"="+value;
			}
		}
	}
	/**
	 * 申请临时token
	 * */
	private void apply(HttpServletRequest request, HttpServletResponse response) throws  IOException {
		// umt_token  和 umt_oauth_token 都需要记下来
		String accessToken=request.getParameter("accessToken");
		OauthLog log=new OauthLog();
		log.setAction(OauthLog.ACTION_VALIDATE_TOKEN_LOGIN_RANDOM_TOKEN);
		log.setIp(RequestUtil.getRemoteIP(request));
		log.setUserAgent(request.getHeader("User-Agent"));
		if(CommonUtils.isNull(accessToken)){
			log.setResult(OauthLog.RESULT_TOKEN_FORMAT_ERROR);
			error(response,"accessToken is Null!",log);
			return;
		}
		OauthToken token=oauthTokenService.getTokenByAccess(accessToken);
		if(token==null){
			log.setResult(OauthLog.RESULT_TOKEN_NOT_FOUND);
			error(response,"can't find accessToken["+accessToken+"]",log);
			return;
		}
		if(token.isAccessExpired()){
			log.setResult(OauthLog.RESULT_TOKEN_EXPIRED);
			error(response,"access Token["+accessToken+"] is Expired!",log);
			return;
		}
		OauthClientBean bean=clientServer.findByClientId(token.getClientId());
		Token randomToken=new Token();
		Date now=new Date();
		randomToken.setCreateTime(now);
		randomToken.setExpireTime(new Date(now.getTime()+5*60*1000));
		randomToken.setUid(Integer.parseInt(token.getUid()));
		randomToken.setRandom(RandomUtil.random(30));
		randomToken.setOperation(Token.OPERATION_WEB_LOGIN);
		randomToken.setStatus(Token.STATUS_UNUSED);
		randomToken.setContent(token.toString());
		randomToken.setPasswordType(token.getPasswordType());
		tokenService.createToken(randomToken);
		log.setUid(Integer.parseInt(token.getUid()));
		User u=userService.getUserByUid(log.getUid());
		log.setCstnetId(u==null?null:u.getCstnetId());
		log.setResult(OauthLog.RESULT_SUCCESS);
		log.setClientId(bean.getClientId());
		log.setClientName(bean.getClientName());
		logService.addLog(log);
		writeToResponse(response,randomToken);
	}
	private void writeToResponse(HttpServletResponse response,Token randomToken) throws IOException{
		// all for ie
		response.setContentType("text/html");
		PrintWriter writer = response.getWriter();
		JSONObject obj=new JSONObject();
		try {
			obj.put("token", randomToken.getRandom()+"-"+randomToken.getId());
			obj.put("createTime", randomToken.getCreateTime().getTime());
			obj.put("expireTime", String.valueOf(randomToken.getExpireTime()));
		} catch (JSONException e) {
			LOGGER.error(e.getMessage(),e);
			return;
		}
		writer.write(obj.toString());
	}
	/**
	 * 验证失败
	 * */
	private void error(HttpServletResponse response,String msg,OauthLog log){
		response.setStatus(403);
		if(log!=null){
			log.setDesc(msg);
			logService.addLog(log);
		}
		LOGGER.error(msg);
	}
}