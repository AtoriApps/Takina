// TODO：太野蛮了，妈的不用Md写文档写你妈Spinx呢？

plugins {
	alias(libs.plugins.sphinx) apply true
}

tasks {
	sphinx {
		println("$projectDir")
		this.setSourceDirectory("$projectDir/src/restructured")
		this.setOutputDirectory("$projectDir/build/docs/sphinx")
		this.setEnvironments(mapOf("ENV_FOO" to "value1"))
		env("ENV_BAZ", "value3")
		tags
	}
}