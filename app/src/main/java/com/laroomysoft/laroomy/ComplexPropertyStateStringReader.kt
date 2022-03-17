package com.laroomysoft.laroomy

import android.util.Log
import java.lang.Exception

class OneParamElement{
    var parameterName = ""
    var parameterValue = ""

    override fun toString(): String {
        return "$parameterName::$parameterValue;;"
    }
    fun clear(){
        this.parameterValue = ""
        this.parameterName = ""
    }
}

class ComplexPropertyStateStringReader {

    fun readOneParameterDefinitions(data: String) : ArrayList<OneParamElement> {

        val resultList = ArrayList<OneParamElement>()

        if(data.isNotEmpty()) {
            try {
                val singleStringList = ArrayList<String>()
                var singleDefinitionString = ""
                var nextValidIndex = 0

                // first record all single definitions
                data.forEachIndexed { index, c ->
                    when (c) {
                        ';' -> {
                            if(nextValidIndex < index) {
                                if (data.length > (index + 2)) {
                                    if (data.elementAt(index + 1) == ';') {
                                        nextValidIndex = index + 2
                                        if(singleDefinitionString.isNotEmpty()) {
                                            singleStringList.add(singleDefinitionString)
                                            singleDefinitionString = ""
                                        }
                                    }
                                }
                            }
                        }
                        '\r' -> {
                            if(singleDefinitionString.isNotEmpty()) {
                                singleStringList.add(singleDefinitionString)
                                singleDefinitionString = ""
                            }
                        }
                        else -> {
                            if(index >= nextValidIndex) {
                                singleDefinitionString += c
                            }
                        }
                    }
                }
                // catch the case that the string was over without delimiter (\r || ;;)
                if(singleDefinitionString.isNotEmpty()){
                    singleStringList.add(singleDefinitionString)
                }
                // process the single definitions
                singleStringList.forEach{

                    val ope = OneParamElement()
                    var isValue = false
                    var param = ""
                    nextValidIndex = 0

                    it.forEachIndexed { charIndex, c ->
                        when(c){
                            ':' -> {
                                if(it.length > (charIndex + 2)){
                                    if(it.elementAt(charIndex + 1) == ':'){
                                        nextValidIndex = charIndex + 2
                                        if(!isValue){
                                            ope.parameterName = param
                                            param = ""
                                            isValue = true
                                        }// else should not be happen!
                                    }
                                }
                            }
                            else -> {
                                param += c
                            }
                        }
                    }
                    if(param.isNotEmpty()){
                        if(isValue){
                            ope.parameterValue = param
                            //param = ""
                            //nextValidIndex = 0
                        }// else should not happen!
                    }
                    resultList.add(ope)
                }
            } catch (e: Exception){
                Log.e("readOneParameterDefinitions", "Exception: $e")
            }
        }
        return resultList
    }
}