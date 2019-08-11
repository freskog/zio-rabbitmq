package freskog.effects

import zio.test.{ RunnableSpec, ZSpec }

abstract class BaseRunnableSpec(spec: => ZSpec[SpecEnv, Nothing, String]) extends RunnableSpec(AppTestRunner)(spec)
