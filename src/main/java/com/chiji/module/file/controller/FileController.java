// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\module\file\controller\FileController.java
package com.chiji.module.file.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.file.dto.UploadUrlRequest;
import com.chiji.module.file.service.FileService;
import com.chiji.module.file.vo.FileUploadVO;
import com.chiji.module.file.vo.UploadUrlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口。
 * <p>
 * 受 Sa-Token 保护，userId 从上下文获取。
 * <p>
 * 小程序上传走「COS 直传」主通道：先调 {@code /upload-url} 取 PUT 预签名地址，
 * 前端把文件直传 COS，再以返回的 url 落库；{@code /upload}（multipart）保留给 Swagger/浏览器联调。
 */
@Tag(name = "文件", description = "媒体文件上传")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传单个媒体文件（multipart，Swagger/浏览器联调用）。
     * <p>
     * 视频时长上限 15 秒，图片不接受 duration。
     *
     * @param file     multipart 文件（表单字段名 file）
     * @param duration 视频时长（秒），图片可不传
     * @return 上传结果（url/type/duration/size/originalName）
     */
    @Operation(summary = "上传媒体文件", description = "图片/视频，视频时长上限 15 秒")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                  @RequestParam(value = "duration", required = false) Integer duration) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(fileService.upload(file, userId, duration), "上传成功");
    }

    /**
     * 获取 COS 直传签名（小程序上传主通道）。
     * <p>
     * 云托管 callContainer 不适合承载文件字节流，改由小程序直传 COS：本接口按 fileName
     * 扩展名判定媒体类型并校验视频时长，签发 PUT 预签名 {@code uploadUrl}；前端 PUT 完成后
     * 使用返回的 {@code url} 作为正式媒体地址（保存记录/反馈/头像时引用）。
     *
     * @param request 文件名（含扩展名）+ 可选视频时长
     * @return 直传签名（uploadUrl 供 PUT，url 为最终访问地址）
     */
    @Operation(summary = "获取直传签名", description = "COS PUT 预签名：uploadUrl 供小程序直传，url 为最终可访问地址")
    @PostMapping(value = "/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<UploadUrlVO> uploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(fileService.createUploadUrl(request.fileName(), request.duration(), userId));
    }
}
