// Minimal stand-ins so the engine's Dagger annotations compile off-Android. They carry no
// behaviour; the engine is a pure function and never uses injection at runtime here.
package javax.inject
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class Inject
@Target(AnnotationTarget.CLASS)
annotation class Singleton
