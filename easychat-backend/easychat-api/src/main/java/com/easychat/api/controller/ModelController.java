package com.easychat.api.controller;

import com.easychat.api.dto.ModelProviderDTO;
import com.easychat.api.dto.ModelRequestDTO;
import com.easychat.api.dto.ProviderDTO;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.model.Result;
import com.easychat.core.service.model.ModelCommand;
import com.easychat.core.service.model.ModelManageService;
import com.easychat.core.service.model.ModelProviderCommand;
import com.easychat.core.service.model.ModelProviderView;
import com.easychat.core.service.model.ProviderCommand;
import com.easychat.core.service.model.ProviderView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/model")
public class ModelController {

    @Autowired
    private ModelManageService modelManageService;

    @PostMapping("/provider/create")
    public Result<Void> addProvider(@RequestBody ProviderDTO dto) {
        try {
            modelManageService.addProvider(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/provider/list")
    public Result<List<ProviderView>> listProviders() {
        return Result.success(modelManageService.listProviders());
    }

    @GetMapping("/provider/{id}")
    public Result<ProviderView> getProvider(@PathVariable Long id) {
        try {
            return Result.success(modelManageService.getProvider(id));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/provider/update")
    public Result<Void> updateProvider(@RequestBody ProviderDTO dto) {
        try {
            modelManageService.updateProvider(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/provider/{providerCode}")
    public Result<Void> deleteProvider(@PathVariable String providerCode) {
        try {
            modelManageService.deleteProvider(providerCode);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<Void> addModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.addModel(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<Void> updateModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.updateModel(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result<Void> deleteModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.deleteModel(dto.getModelCode());
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/provider/{providerCode}/models")
    public Result<Void> addModelToProvider(@PathVariable String providerCode,
                                           @RequestBody ModelProviderDTO dto) {
        try {
            modelManageService.addModelToProvider(providerCode, toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/provider/{providerCode}/models")
    public Result<List<ModelProviderView>> listModelsByProvider(@PathVariable String providerCode) {
        return Result.success(modelManageService.listModelsByProvider(providerCode));
    }

    @DeleteMapping("/provider/{providerCode}/models/{modelCode}")
    public Result<Void> deleteModelProvider(@PathVariable String providerCode,
                                            @PathVariable String modelCode) {
        modelManageService.deleteModelProvider(providerCode, modelCode);
        return Result.success();
    }

    private ProviderCommand toCommand(ProviderDTO dto) {
        ProviderCommand command = new ProviderCommand();
        command.setProviderCode(dto.getProviderCode());
        command.setBaseUrl(dto.getBaseUrl());
        command.setApiKey(dto.getApiKey());
        command.setEnabled(dto.getEnabled());
        return command;
    }

    private ModelCommand toCommand(ModelRequestDTO dto) {
        ModelCommand command = new ModelCommand();
        command.setModelCode(dto.getModelCode());
        command.setMaxTokens(dto.getMaxTokens());
        command.setDefaultConfig(dto.getDefaultConfig());
        command.setEnabled(dto.getEnabled());
        return command;
    }

    private ModelProviderCommand toCommand(ModelProviderDTO dto) {
        ModelProviderCommand command = new ModelProviderCommand();
        command.setModelCode(dto.getModelCode());
        command.setPriority(dto.getPriority());
        command.setWeight(dto.getWeight());
        command.setTimeoutMs(dto.getTimeoutMs());
        command.setMaxRetry(dto.getMaxRetry());
        command.setEnabled(dto.getEnabled());
        return command;
    }
}
