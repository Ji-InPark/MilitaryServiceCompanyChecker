package com.example.militaryservicecompanychecker.company.service

import com.example.militaryservicecompanychecker.company.entity.Company
import com.example.militaryservicecompanychecker.company.enums.GovernmentLocation
import com.example.militaryservicecompanychecker.company.enums.Sector
import com.example.militaryservicecompanychecker.company.enums.ServiceType
import com.example.militaryservicecompanychecker.company.repository.CompanyRepository
import com.example.militaryservicecompanychecker.company.util.EnumUtil.toGovernmentLocation
import com.example.militaryservicecompanychecker.company.util.EnumUtil.toSector
import com.example.militaryservicecompanychecker.company.util.Util.toCompanyKeyword
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.springframework.boot.json.GsonJsonParser
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import javax.transaction.Transactional

@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val okHttpClient: OkHttpClient
) {
    fun searchCompanyByRegex(regex: String): List<Company> {
        return companyRepository.findTop5ByCompanyNameRegex(regex)
    }

    fun searchCompany(
        searchName: String,
        governmentLocation: GovernmentLocation?,
        sector: Sector?
    ): List<Company> {
        return if (governmentLocation == null && sector == null)
            companyRepository.findAllByCompanyNameContainsOrderByIdAsc(searchName)
        else
            companyRepository.findAllByGovernmentLocationOrCompanySectorAndCompanyName(
                searchName,
                governmentLocation?.toString(),
                sector?.toString()
            )
    }

    fun getGovernmentLocations(): Array<GovernmentLocation> {
        return GovernmentLocation.values()
    }

    fun getSectors(): Array<Sector> {
        return Sector.values()
    }

    fun getKreditJobKeyAndUpdateToCompany(id: Long): String {
        val company = companyRepository.findById(id).orElseThrow()

        if (company.kreditJobKey != null) return company.kreditJobKey!!

        val kreditJobKey = getKreditJobKey(company.companyKeyword)
        company.kreditJobKey = kreditJobKey
        companyRepository.saveAndFlush(company)

        return kreditJobKey
    }

    fun test() {
        val serviceTypeMap = mapOf(
            1 to ServiceType.산업기능요원,
            2 to ServiceType.전문연구요원,
            3 to ServiceType.승선근무예비역,
        )

        for (serviceTypeKey in 1..3) {
            val body = FormBody.Builder()
                .add("eopjong_gbcd", "3")
                .build()
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url("https://work.mma.go.kr/caisBYIS/search/downloadBYJJEopCheExcel.do")
                    .post(body)
                    .build()
            ).execute()

            val stream = ByteArrayInputStream(response.body?.bytes())
            val workbook = HSSFWorkbook(stream)

        }

    }

    private fun getKreditJobKey(companyKeyword: String): String {
        val body =
            """{"q": "$companyKeyword"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val response = okHttpClient.newCall(
            Request.Builder()
                .url("https://kreditjob.com/api/search/company")
                .post(body)
                .build()
        ).execute()

        return GsonJsonParser().parseMap(response.body?.string())["PK_NM_HASH"].toString()
    }

    @Transactional
    fun deleteAndCreateCompanyByFile(): MutableList<Company> {
        companyRepository.deleteAllInBatch()

        val companies = createCompaniesFromBYIS()

        return companyRepository.saveAllAndFlush(companies)
    }

    private fun createCompaniesFromBYIS(): MutableList<Company> {
        val serviceTypeMap = mapOf(
            1 to ServiceType.산업기능요원,
            2 to ServiceType.전문연구요원,
            3 to ServiceType.승선근무예비역,
        )

        val companies = mutableListOf<Company>()

        for ((key, value) in serviceTypeMap) {
            val response = requestCompanyInfosToBYIS(key)
            val workbook = convertBYISResponseToWorkbook(response)
            companies.addAll(createCompanies(workbook, value))
        }

        return companies
    }

    private fun createCompanies(
        workbook: HSSFWorkbook,
        serviceType: ServiceType
    ): MutableList<Company> {
        val sheet = workbook.getSheetAt(0)
        val headerMap =
            sheet.getRow(0).toList().withIndex().associate { it.value.toString() to it.index }

        val companies = mutableListOf<Company>()
        for (i in 1.until(sheet.physicalNumberOfRows)) {
            val row = sheet.getRow(i)
            val companyName = row.getCell(headerMap["업체명"]!!).stringCellValue
            val newCompany = Company(
                companyName = companyName,
                governmentLocation = row.getCell(headerMap["지방청"]!!).stringCellValue.toGovernmentLocation(),
                companyLocation = row.getCell(headerMap["주소"]!!).stringCellValue,
                companyPhoneNumber = row.getCell(headerMap["전화번호"]!!).stringCellValue,
                companyFaxNumber = row.getCell(headerMap["팩스번호"]!!).stringCellValue,
                companySector = row.getCell(headerMap["업종"]!!).stringCellValue.toSector(),
                companyScale = row.getCell(headerMap["기업규모"]!!).stringCellValue,
                serviceType = serviceType,
                companyKeyword = companyName.toCompanyKeyword()
            )
            companies.add(newCompany)
        }

        return companies
    }

    private fun convertBYISResponseToWorkbook(response: Response): HSSFWorkbook {
        val stream = ByteArrayInputStream(response.body?.bytes())
        return HSSFWorkbook(stream)
    }

    private fun requestCompanyInfosToBYIS(serviceTypeNumber: Int): Response {
        val body = FormBody.Builder()
            .add("eopjong_gbcd", serviceTypeNumber.toString())
            .build()
        return okHttpClient.newCall(
            Request.Builder()
                .url("https://work.mma.go.kr/caisBYIS/search/downloadBYJJEopCheExcel.do")
                .post(body)
                .build()
        ).execute()
    }

    fun getServiceTypes(): Array<ServiceType> {
        return ServiceType.values()
    }
}