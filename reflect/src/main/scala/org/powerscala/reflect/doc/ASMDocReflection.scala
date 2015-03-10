package org.powerscala.reflect.doc

import java.lang.reflect.{Constructor, Method}
import org.objectweb.asm.{ClassReader, Type}
import org.objectweb.asm.tree.{LocalVariableNode, MethodNode, ClassNode}

import scala.collection.JavaConversions._

/**
 * @author Matt Hicks <mhicks@powerscala.org>
 */
class ASMDocReflection(clazz: Class[_]) extends DocumentationReflection {
  lazy val classNode = ASMDocReflection.classNode(clazz)
  lazy val methods = classNode.methods.toList.asInstanceOf[List[MethodNode]]
  private var documentation = Map.empty[Method, MethodDocumentation]
  private var constructorDocumentation = Map.empty[Constructor[_], MethodDocumentation]

  def method(m: Method) = documentation.get(m) match {
    case Some(doc) => doc
    case None => generateDocumentation(m)
  }

  def constructor(c: Constructor[_]) = constructorDocumentation.get(c) match {
    case Some(doc) => doc
    case None => generateDocumentation(c)
  }

  private def generateDocumentation(m: Method) = synchronized {
    try {
      val name = m.getName
      val returnClass = DocumentedClass(null, m.getReturnType, None)
      val md = if (m.getParameterTypes.length > 0) {
        val desc = Type.getMethodDescriptor(m)
        val results = methods.collect {
          case methodNode if (methodNode.name == name && methodNode.desc == desc) => methodNode
        }
        if (results.length != 1) {
          throw new RuntimeException("%s methodNodes with the supplied signature: %s".format(results.length, m))
        }
        val mn = results.head
        val variables = mn.localVariables
        val args = if (variables.length == 0) {
          m.getParameterTypes.zipWithIndex.map {
            case (c, index) => DocumentedClass("arg%s".format(index), c, None)
          }.toList
        } else {
          val argNames = variables.tail.map(lvn => lvn.asInstanceOf[LocalVariableNode].name).toList
          argNames.zip(m.getParameterTypes).map {
            case (n, c) => DocumentedClass(n, c, None)
          }
        }
        MethodDocumentation(args, returnClass, null, None)
      } else {
        MethodDocumentation(Nil, returnClass, null, None)
      }
      documentation += m -> md
      md
    } catch {
      case t: Throwable => throw new RuntimeException("Unable to generate documentation for %s".format(m), t)
    }
  }

  private def generateDocumentation(c: Constructor[_]) = synchronized {
    try {
      val returnClass = DocumentedClass(null, clazz, None)
      val md = if (c.getParameterTypes.length > 0) {
        val desc = Type.getConstructorDescriptor(c)
        val results = methods.collect {
          case methodNode if (methodNode.name == "<init>" && methodNode.desc == desc) => methodNode
        }
        if (results.length != 1) {
          throw new RuntimeException("%s methodNodes with the supplied signature: %s".format(results.length, c))
        }
        val mn = results.head
        val variables = mn.localVariables
        val args = if (variables.length == 0) {
          c.getParameterTypes.zipWithIndex.map {
            case (cl, index) => DocumentedClass("arg%s".format(index), cl, None)
          }.toList
        } else {
          val argNames = variables.tail.map(lvn => lvn.asInstanceOf[LocalVariableNode].name).toList
          argNames.zip(c.getParameterTypes).map {
            case (n, cl) => DocumentedClass(n, cl, None)
          }
        }
        MethodDocumentation(args, returnClass, null, None)
      } else {
        MethodDocumentation(Nil, returnClass, null, None)
      }
      constructorDocumentation += c -> md
      md
    } catch {
      case t: Throwable => throw new RuntimeException("Unable to generate documentation for %s".format(c), t)
    }
  }
}

object ASMDocReflection extends DocMapper {
  def apply(c: Class[_]) = new ASMDocReflection(c)

  def classNode(clazz: Class[_]) = try {
    val classLoader = Thread.currentThread().getContextClassLoader
    val declaringType = Type.getType(clazz)
    val url = declaringType.getInternalName + ".class"
    val classNode = new ClassNode()
    try {
      val input = classLoader.getResourceAsStream(url) match {
        case null => getClass.getClassLoader.getResourceAsStream(url)
        case i => i
      }
      try {
        val classReader = new ClassReader(input)
        classReader.accept(classNode, 0)
      } finally {
        input.close()
      }
      classNode
    } catch {
      case exc: NullPointerException => throw new NullPointerException("Unable to look up class %s by url %s".format(declaringType.getClassName, url))
    }
  } catch {
    case t: Throwable => throw new RuntimeException(s"Unable to do ASM reflection on ${clazz.getName}.", t)
  }
}