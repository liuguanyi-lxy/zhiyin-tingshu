package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	@Autowired
	private UserInfoMapper userInfoMapper;

	@Override
	public UserInfoVo getUserInfoVoByUserId(Long userId) {
		//	获取到用户信息对象
		UserInfo userInfo = this.getById(userId);
		if (userInfo == null) {
			// 抛出自定义异常或返回 null，视业务需求而定
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH); // 假设这是你的自定义异常
		}
		//	创建UserInfoVo 对象
		UserInfoVo userInfoVo = new UserInfoVo();
		//	属性拷贝
		BeanUtils.copyProperties(userInfo,userInfoVo);
		return userInfoVo;
	}
}