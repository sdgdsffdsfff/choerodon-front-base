package io.choerodon.base.infra.aspect;

import io.choerodon.base.infra.annotation.OperateLog;
import io.choerodon.base.infra.dto.OperateLogDTO;
import io.choerodon.base.infra.dto.OrganizationDTO;
import io.choerodon.base.infra.dto.ProjectDTO;
import io.choerodon.base.infra.dto.UserDTO;
import io.choerodon.base.infra.mapper.OperateLogMapper;
import io.choerodon.base.infra.mapper.OrganizationMapper;
import io.choerodon.base.infra.mapper.ProjectMapper;
import io.choerodon.base.infra.mapper.UserMapper;
import io.choerodon.core.enums.ResourceType;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

/**
 * User: Mr.Wang
 * Date: 2020/2/25
 */
@Aspect
@Component
@Transactional(rollbackFor = Exception.class)
public class OperateLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperateLogAspect.class);

    //解锁用户
    private static final String unlockUser = "unlockUser";
    //启用用户
    private static final String enableUser = "enableUser";
    //禁用用户
    private static final String disableUser = "disableUser";
    //删除组织管理员角色
    private static final String deleteOrgAdministrator = "deleteOrgAdministrator";
    //添加管理员角色
    private static final String createOrgAdministrator = "createOrgAdministrator";
    //组织层修改组织的信息
    private static final String updateOrganization = "updateOrganization";
    //启用组织
    private static final String enableOrganization = "enableOrganization";
    //停用组织
    private static final String disableOrganization = "disableOrganization";
    //创建项目
    private static final String createProject = "createProject";
    //启用项目
    private static final String enableProject = "enableProject";
    //禁用项目
    private static final String disableProject = "disableProject";

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private ProjectMapper projectMapper;


    @Pointcut("bean(*ServiceImpl) && @annotation(io.choerodon.base.infra.annotation.OperateLog)")
    public void updateMethodPointcut() {
        throw new UnsupportedOperationException();
    }


    @Around("updateMethodPointcut()")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        Long operatorId = DetailsHelper.getUserDetails().getUserId();
        OperateLogDTO operateLogDTO = new OperateLogDTO();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();
        Map<Object, Object> parmMap = new HashMap<>();
        parmMap = processParameters(parameterNames, args);
        OperateLog operateLog = method.getAnnotation(OperateLog.class);
        String type = operateLog.type();
        String content = operateLog.content();
        ResourceType[] level = operateLog.level();
        List<String> contentList = new ArrayList<>();
        Long organizationId = null;
        if (null != operateLog && null != method && null != type) {
            switch (type) {
                case unlockUser:
                    contentList.add(handleUnlockUserOperateLog(content, operatorId, parmMap));
                    organizationId = getOrganizationId(parmMap);
                    break;
                case enableUser:
                    contentList.add(handleCommonOperateLog(content, operatorId, parmMap));
                    organizationId = getOrganizationId(parmMap);
                    break;
                case disableUser:
                    contentList.add(handleCommonOperateLog(content, operatorId, parmMap));
                    organizationId = getOrganizationId(parmMap);
                    break;
                case deleteOrgAdministrator:
                    contentList.add(handleCommonOperateLog(content, operatorId, parmMap));
                    organizationId = getOrganizationId(parmMap);
                    break;
                case createOrgAdministrator:
                    contentList = handleCreateOrgAdministratorOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                case updateOrganization:
                    contentList = handleOrganizationOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                case enableOrganization:
                    contentList = handleOrganizationOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                case disableOrganization:
                    contentList = handleOrganizationOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                case createProject:
                    contentList = handleCreateProjectOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationIdByProject(parmMap);
                    break;
                case enableProject:
                    contentList = handleProjectOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                case disableProject:
                    contentList = handleProjectOperateLog(content, operatorId, parmMap);
                    organizationId = getOrganizationId(parmMap);
                    break;
                default:
                    break;
            }
        }


        Object object = null;
        operateLogDTO.setOperatorId(operatorId);
        operateLogDTO.setSuccess(true);
        operateLogDTO.setMethod(method.getName());
        operateLogDTO.setType(type);
        operateLogDTO.setSourceId(organizationId);
        try {
            object = pjp.proceed();
        } catch (Throwable e) {
            operateLogDTO.setSuccess(false);
            contentList.forEach(s -> {
                operateLogDTO.setContent(s);
                Stream.of(level).forEach(v -> {
                    if (ResourceType.SITE.value().equals(v.value())) {
                        operateLogDTO.setSourceId(0L);
                    }
                    operateLogDTO.setSourceType(v.value());
                    if (operateLogMapper.insert(operateLogDTO) != 1) {
                        LOGGER.info("error.insert.operate.failure.log:{}", e.getMessage());
                    }
                });
            });
            throw e;
        }
        contentList.forEach(s -> {
            operateLogDTO.setContent(s);
            Stream.of(level).forEach(v -> {
                if (ResourceType.SITE.value().equals(v.value())) {
                    operateLogDTO.setSourceId(0L);
                }
                operateLogDTO.setSourceType(v.value());
                if (operateLogMapper.insert(operateLogDTO) != 1) {
                    LOGGER.info("error.insert.operate.success.log");
                }
            });
        });
        return object;
    }

    private Long getOrganizationIdByProject(Map<Object, Object> parmMap) {
        ProjectDTO projectDTO = (ProjectDTO) parmMap.get("projectDTO");
        return projectDTO.getOrganizationId();
    }

    private Long getOrganizationId(Map<Object, Object> parmMap) {
        return (Long) parmMap.get("organizationId");
    }

    private Map<Object, Object> processParameters(String[] parameterNames, Object[] args) {
        Map<Object, Object> objectMap = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            objectMap.put(parameterNames[i], args[i]);
        }
        return objectMap;
    }

    private List<String> handleProjectOperateLog(String content, Long operatorId, Map map) {
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        Long projectId = (Long) map.get("projectId");
        ProjectDTO projectDTO = projectMapper.selectByPrimaryKey(projectId);
        List<String> contentList = new ArrayList<>();
        if (Objects.isNull(operator) || Objects.isNull(projectDTO)) {
            return contentList;
        }
        String format = String.format(content, operator.getRealName(), projectDTO.getName());
        contentList.add(format);
        return contentList;
    }

    private List<String> handleCreateProjectOperateLog(String content, Long operatorId, Map map) {
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        ProjectDTO projectDTO = (ProjectDTO) map.get("projectDTO");
        List<String> contentList = new ArrayList<>();
        if (Objects.isNull(operator)) {
            return contentList;
        }
        String format = String.format(content, operator.getRealName(), projectDTO.getName());
        contentList.add(format);
        return contentList;
    }

    private List<String> handleOrganizationOperateLog(String content, Long operatorId, Map map) {
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        OrganizationDTO organizationDTO = organizationMapper.selectByPrimaryKey(map.get("organizationId"));
        List<String> contentList = new ArrayList<>();
        if (Objects.isNull(operator) || Objects.isNull(organizationDTO)) {
            return contentList;
        }
        String format = String.format(content, operator.getRealName(), organizationDTO.getName());
        contentList.add(format);
        return contentList;
    }


    private List<String> handleCreateOrgAdministratorOperateLog(String content, Long operatorId, Map map) {
        List<Long> userIds = (List<Long>) map.get("userIds");
        List<String> contentList = new ArrayList<>();
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        if (Objects.isNull(operator)) {
            return contentList;
        }
        userIds.forEach(userId -> {
            String parms = getParms(operatorId, userId);
            contentList.add(String.format(content, parms, operator.getRealName()));
        });
        return contentList;
    }


    private String handleCommonOperateLog(String content, Long operatorId, Map map) {
        String parms = getParms(operatorId, (Long) map.get("userId"));
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        return String.format(content, parms, operator.getRealName());
    }

    private String handleUnlockUserOperateLog(String content, Long operatorId, Map map) {
        String parms = getParms(operatorId, (Long) map.get("userId"));
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        return String.format(content, operator.getRealName(), parms);
    }

    private String getParms(Long operatorId, Long userId) {
        UserDTO operator = userMapper.selectByPrimaryKey(operatorId);
        UserDTO targeter = userMapper.selectByPrimaryKey(userId);
        if (!Objects.isNull(operator) && !Objects.isNull(targeter)) {
            return targeter.getId() + "(" + targeter.getRealName() + ")";
        }
        throw new CommonException("error.query.user");
    }
}
