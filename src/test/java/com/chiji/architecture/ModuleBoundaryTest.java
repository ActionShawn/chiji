// D:\Java Work Place\personal-develop\teeth-trace\chiji\src\test\java\com\chiji\architecture\ModuleBoundaryTest.java
package com.chiji.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试（ArchUnit）。
 * <p>
 * 约束分层依赖方向：
 * <ul>
 *     <li>common（通用层）不得依赖 framework 与 module；</li>
 *     <li>framework（技术能力层）不得依赖 module；</li>
 *     <li>module 内的 mapper 仅允许被 module 的 service 层类访问（Controller / support /
 *         job 等不得绕过 Service 直接使用 Mapper）。</li>
 * </ul>
 * <p>
 * 采用普通 JUnit5 {@link Test} + {@link ClassFileImporter} 直接驱动 {@link ArchRule#check(JavaClasses)}，
 * 取代 {@code @AnalyzeClasses}/{@code @ArchTest} 的测试引擎发现方式——后者在本仓库经 surefire 上报
 * {@code Tests run: 0}（{@code @ArchTest} 静态字段未被注册为用例），导致约束空转。
 * 本测试不依赖 Spring 上下文与数据库，可直接以纯单元测试方式运行。
 */
public class ModuleBoundaryTest {

    /** 工程全量类（com.chiji 下 main 与 test 类均被导入，作为规则校验对象）。 */
    private static final JavaClasses PROJECT_CLASSES = new ClassFileImporter().importPackages("com.chiji");

    /** common 层不得依赖 framework 层与 module 层。 */
    private static final ArchRule COMMON_LAYERING_RULE = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat().resideInAnyPackage("..framework..", "..module..");

    /** framework 层不得依赖 module 层。 */
    private static final ArchRule FRAMEWORK_LAYERING_RULE = noClasses()
            .that().resideInAPackage("..framework..")
            .should().dependOnClassesThat().resideInAPackage("..module..");

    /**
     * module 内的 mapper 仅允许被 module 的 service 层类依赖。
     * <p>
     * 必须用依赖图语义（{@code onlyHaveDependentClassesThat}）而非指令级访问
     * （{@code onlyBeAccessed}）：MyBatis mapper 是空接口，查询方法全部继承自
     * {@code BaseMapper}，指令级调用目标落在外部 {@code BaseMapper} 上，mapper 子接口自身
     * 永远没有 access，基于 access 的写法会空转；依赖图语义能捕获字段声明、方法入参、
     * 方法调用等全部类型引用，使 Controller / support / job 绕过 Service 直连 Mapper 的
     * 违规可被真实拦截。
     */
    private static final ArchRule MAPPER_ACCESS_RULE = classes()
            .that().resideInAPackage("..module..mapper..")
            .should().onlyHaveDependentClassesThat().resideInAnyPackage("..module..service..");

    @Test
    void common_must_not_depend_on_framework_or_module() {
        COMMON_LAYERING_RULE.check(PROJECT_CLASSES);
    }

    @Test
    void framework_must_not_depend_on_module() {
        FRAMEWORK_LAYERING_RULE.check(PROJECT_CLASSES);
    }

    @Test
    void module_mapper_only_accessed_by_service_layer() {
        MAPPER_ACCESS_RULE.check(PROJECT_CLASSES);
    }
}
