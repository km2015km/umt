/*
 * Copyright (c) 2008-2013 Computer Network Information Center (CNIC), Chinese Academy of Sciences.
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
package cn.vlabs.duckling.api.umt.sso.configable.msg;

/**
 * @author lvly
 * @since 2013-4-24
 */
public class Message {
	private Reason reason;
	private String description;
	public Message(Reason reason,String description){
		this.reason=reason;
		this.description=description;
	}
	public Reason getReason() {
		return reason;
	}
	public String getDescription() {
		return description;
	}

}