// D:\Java Work Place\personal-develop\teeth-trace\server\src\test\java\com\chiji\architecture\ModuleBoundaryTest.java
package com.chiji.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试（ArchUnit）。
 * <p>
 * 约束分层依赖方向：
 * <ul>
 *     <li>common（通用层）不得依赖 framework 与 module；</li>
 *     <li>framework（技术能力层）不得依赖 module；</li>
 *     <li>module 内的 mapper 仅允许被同模块的 service 访问。</li>
 * </ul>
 * 本测试不依赖 Spring 上下文与数据库，可直接以纯单元测试方式运行。
 */
@AnalyzeClasses(packages = "com.chiji")
public class ModuleBoundaryTest {

    /** common 层不得依赖 framework 层与 module 层。 */
    @ArchTest
    static final ArchRule common_must_not_depend_on_framework_or_module = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat().resideInAnyPackage("..framework..", "..module..");

    /** framework 层不得依赖 module 层。 */
    @ArchTest
    static final ArchRule framework_must_not_depend_on_module = noClasses()
            .that().resideInAPackage("..framework..")
            .should().dependOnClassesThat().resideInAPackage("..module..");

    /**
     * module 内的 mapper 仅允许被同模块的 service 访问。
     * <p>
     * 当前 module 尚未落地（无任何类），本规则空转通过；
     * 业务模块落地后自动生效，用于约束 Controller 等不得绕过 Service 直接使用 Mapper。
     */
    @ArchTest
    static final ArchRule module_mapper_only_accessed_by_same_module_service = classes()
            .that().resideInAPackage("..module..mapper..")
            .should().onlyBeAccessed().byAnyPackage("..module..service..");
}
