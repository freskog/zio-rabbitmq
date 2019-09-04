package freskog.effects

import zio.test.{ RunnableSpec, ZSpec }

abstract class BaseRunnableSpec(spec: => ZSpec[SpecEnv, Nothing, String, Any]) extends RunnableSpec(AppTestRunner)(spec)
