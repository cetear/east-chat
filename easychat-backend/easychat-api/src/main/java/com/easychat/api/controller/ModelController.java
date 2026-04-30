package com.easychat.api.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.api.dto.AddModelRequestDTO;
import com.easychat.api.dto.ModelProviderDTO;
import com.easychat.api.dto.ModelRequestDTO;
import com.easychat.api.dto.ProviderDTO;
import com.easychat.common.model.Result;
import com.easychat.infra.mysql.entity.ModelDO;
import com.easychat.infra.mysql.entity.ModelProviderDO;
import com.easychat.infra.mysql.entity.ProviderDO;
import com.easychat.infra.mysql.mapper.ModelMapper;
import com.easychat.infra.mysql.mapper.ModelProviderMapper;
import com.easychat.infra.mysql.mapper.ProviderMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/model")
public class ModelController {

    @Autowired
    private ProviderMapper providerMapper;

    @Autowired
    private ModelProviderMapper modelProviderMapper;

    @Autowired
    private ModelMapper modelMapper;

    /** ==================== 渠道商 CRUD ==================== **/

    @PostMapping("/provider/creat")
    public Result<Void> addProvider(@RequestBody ProviderDTO dto) {
        ProviderDO entity = new ProviderDO();
        entity.setProviderCode(dto.getProviderCode());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setApiKey(dto.getApiKey());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : 1);
        entity.setFailCount(0);
        entity.setCircuitStatus("CLOSED");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        providerMapper.insert(entity);
        return Result.success();
    }

    @GetMapping("/provider/list")
    public Result<List<ProviderDO>> listProviders() {
        List<ProviderDO> list = providerMapper.selectList(null);
        return Result.success(list);
    }

    @GetMapping("/provider/{id}")
    public Result<ProviderDO> getProvider(@PathVariable Long id) {
        ProviderDO entity = providerMapper.selectById(id);
        if (entity == null) {
            return Result.error("渠道商不存在");
        }
        return Result.success(entity);
    }

    @PostMapping("/provider/update")
    public Result<Void> updateProvider(@RequestBody ProviderDTO dto) {
        QueryWrapper<ProviderDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("provider_code", dto.getProviderCode());
        ProviderDO providerDO = providerMapper.selectOne(queryWrapper);
        if (providerDO == null) {
            return Result.error("渠道商不存在");
        }
        if (dto.getBaseUrl() != null) {
            providerDO.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getApiKey() != null) {
            providerDO.setApiKey(dto.getApiKey());
        }
        if (dto.getEnabled() != null) {
            providerDO.setEnabled(dto.getEnabled());
        }
        providerDO.setUpdatedAt(LocalDateTime.now());
        providerMapper.updateById(providerDO);
        return Result.success();
    }

    @DeleteMapping("/provider/{providerCode}")
    public Result<Void> deleteProvider(@PathVariable String providerCode) {
        QueryWrapper<ProviderDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("provider_code", providerCode);
        ProviderDO providerDO = providerMapper.selectOne(queryWrapper);
        if (providerDO == null) {
            return Result.error("渠道商不存在");
        }
        providerMapper.deleteById(providerDO.getId());
        return Result.success();
    }

    /** ==================== 模型 CRUD ==================== **/
    @PostMapping("/create")
    public Result<Void> addModel(@RequestBody ModelRequestDTO modelRequestDTO) {
        QueryWrapper<ModelDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("model_code", modelRequestDTO.getModelCode());
        ModelDO modelDO = modelMapper.selectOne(queryWrapper);
        if (modelDO != null) {
            throw new RuntimeException();
        } else {
            modelDO = new ModelDO();
            modelDO.setEnabled(modelRequestDTO.getEnabled());
            modelDO.setModelCode(modelRequestDTO.getModelCode());
            modelDO.setDefaultConfig(modelRequestDTO.getDefaultConfig());
            modelDO.setMaxTokens(modelRequestDTO.getMaxTokens());
            modelDO.setCreatedAt(LocalDateTime.now());
            modelDO.setUpdatedAt(LocalDateTime.now());
            modelMapper.insert(modelDO);
        }
        return Result.success();
    }

    @PostMapping("/update")
    public Result<Void> updateModel(@RequestBody ModelRequestDTO modelRequestDTO) {
        QueryWrapper<ModelDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("model_code", modelRequestDTO.getModelCode());
        ModelDO modelDO = modelMapper.selectOne(queryWrapper);
        if (modelDO == null) {
            throw new RuntimeException();
        } else {
            modelDO.setEnabled(modelRequestDTO.getEnabled());
            modelDO.setModelCode(modelRequestDTO.getModelCode());
            modelDO.setDefaultConfig(modelRequestDTO.getDefaultConfig());
            modelDO.setMaxTokens(modelRequestDTO.getMaxTokens());
            modelDO.setUpdatedAt(LocalDateTime.now());
            modelMapper.updateById(modelDO);
        }
        return Result.success();
    }

    @PostMapping("/delete")
    public Result<Void> deleteModel(@RequestBody ModelRequestDTO modelRequestDTO) {
        QueryWrapper<ModelDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("model_code", modelRequestDTO.getModelCode());
        ModelDO modelDO = modelMapper.selectOne(queryWrapper);
        if (modelDO == null) {
            throw new RuntimeException();
        } else {
            modelDO.setEnabled(modelRequestDTO.getEnabled());
            modelDO.setModelCode(modelRequestDTO.getModelCode());
            modelDO.setDefaultConfig(modelRequestDTO.getDefaultConfig());
            modelDO.setMaxTokens(modelRequestDTO.getMaxTokens());
            modelDO.setUpdatedAt(LocalDateTime.now());
            modelMapper.updateById(modelDO);
        }
        return Result.success();
    }


    /** ==================== 指定渠道商下模型路由配置 CRUD ==================== **/

    @PostMapping("/provider/addModelToProvider")
    public Result<Void> addModelToProvider(@PathVariable String providerCode,
                                           @RequestBody ModelProviderDTO addModelrequestDto) {
        ProviderDO provider = providerMapper.selectOne(
                new LambdaQueryWrapper<ProviderDO>().eq(ProviderDO::getProviderCode, providerCode));
        if (provider == null) {
            return Result.error("渠道商不存在");
        }
        ModelProviderDO modelProviderDO =
                modelProviderMapper.selectOne(new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getProviderCode, providerCode));
        ModelProviderDO entity = new ModelProviderDO();
        entity.setModelCode(addModelrequestDto.getModelCode());
        entity.setProviderCode(providerCode);
        entity.setPriority(addModelrequestDto.getPriority() != null ? addModelrequestDto.getPriority() : 0);
        entity.setWeight(addModelrequestDto.getWeight() != null ? addModelrequestDto.getWeight() : 1);
        entity.setTimeoutMs(addModelrequestDto.getTimeoutMs() != null ? addModelrequestDto.getTimeoutMs() : 60000);
        entity.setMaxRetry(addModelrequestDto.getMaxRetry() != null ? addModelrequestDto.getMaxRetry() : 0);
        entity.setEnabled(addModelrequestDto.getEnabled() != null ? addModelrequestDto.getEnabled() : 1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        modelProviderMapper.insert(entity);
        return Result.success();
    }

    @PostMapping("/provider/listModelsByProvider")
    public Result<List<ModelProviderDO>> listModelsByProvider(@Param("providerCode") String providerCode) {
        List<ModelProviderDO> list = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getProviderCode, providerCode));
        return Result.success(list);
    }


    @DeleteMapping("/provider/deleteModelProvider")
    public Result<Void> deleteModelProvider(@Param("providerCode") String providerCode, @Param("modelCode") String modelCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelProviderDO>()
                        .eq(ModelProviderDO::getModelCode, modelCode)
                        .eq(ModelProviderDO::getProviderCode, providerCode));
        return Result.success();
    }
}
